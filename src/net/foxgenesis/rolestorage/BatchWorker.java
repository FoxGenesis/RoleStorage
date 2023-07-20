package net.foxgenesis.rolestorage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.foxgenesis.executor.PrefixedThreadFactory;

/**
 * Class used to store insert/delete batch data and then execute when a
 * threshold is reached.
 * 
 * @author Ashley
 *
 */
public class BatchWorker implements RoleBatchWorker {

	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger("BatchWorker");

	/**
	 * Thread Factory
	 */
	private static final ThreadFactory pool = new PrefixedThreadFactory("RoleStorage");

	/**
	 * Batch data to work with
	 */

	private final BatchData<Queue<long[]>> batchData;

	/**
	 * Worker thread
	 */

	private final Thread thread;

	/**
	 * Conditional for whether the worker thread is running or not
	 */

	private AtomicBoolean running = new AtomicBoolean();

	/**
	 * Conditional for whether the worker thread is currently processing the queues
	 */

	private AtomicBoolean processing = new AtomicBoolean(false);

	/**
	 * Conditional for whether the queues should be flushed
	 */

	private AtomicBoolean flush = new AtomicBoolean();

	/**
	 * Create a new instance using the provided {@link BatchData}.
	 * 
	 * @param batchData - data holder to be used
	 */
	BatchWorker(BatchData<Queue<long[]>> batchData) {
		this.batchData = Objects.requireNonNull(batchData);
		this.thread = pool.newThread(this::run);
	}

	/**
	 * Runnable method for worker thread.
	 */
	private void run() {
		try (Connection conn = batchData.source();
				PreparedStatement insertStatement = conn.prepareStatement(batchData.insertStatement().get());
				PreparedStatement removeStatement = conn.prepareStatement(batchData.removeStatement().get())) {
			logger.debug("Worker ready");
			running.set(true);
			// Main loop
			while (running.get()) {
				synchronized (batchData) {
					try {
						// Check if we have reached the threshold
						while (!batchData.thresholdReached()) {

							// We have not reached the threshold. Wait for notify
							logger.trace("Waiting");
							batchData.wait();

							// Thread has woken up
							if (flush.get()) {
								logger.trace("Flushing queue...");
								flush.set(false);
								break;
							} else if (batchData.thresholdReached()) {
								logger.trace("Threshold reached ({}/{}). Executing batch...", batchData.size(),
										batchData.threshold());
							} else
								logger.warn("Spurious wakup");
						}
					} catch (InterruptedException e) {
						// Called when we want to halt execution of this thread
						logger.trace("Worker interupt recieved (r={},f={},p={})!", running, flush, processing);
					}

					// Process both queues
					process(insertStatement, removeStatement);
				}
			}

			logger.debug("Stopping worker...");
		} catch (SQLException e1) {
			logger.error("Exception in worker thread", e1);
		}
	}

	/***
	 * Process both queues.
	 * 
	 * @param insert - prepared insert statement
	 * @param delete - prepared delete statement
	 */
	private void process(PreparedStatement insert, PreparedStatement delete) {
		// Process insert queue
		processQueue(insert, batchData.insertQueue(), flush.get(), (statement, size, initialSize) -> {
			logger.trace("Processing {} insert statements. (Queue Size = {})", size, initialSize);
			statement.executeBatch();
		}, error -> logger.error("Error while processing insert queue", error));

		// Process remove queue
		processQueue(delete, batchData.removeQueue(), flush.get(), (statement, size, initialSize) -> {
			logger.trace("Processing {} remove statements. (Queue Size = {})", size, initialSize);
			statement.executeBatch();
		}, error -> logger.error("Error while processing remove queue", error));

		processing.set(false);
	}

	/**
	 * Process a queue containing batch data for a prepared statement. If
	 * {@code flush} is {@code true}, the threshold will be ignored.
	 * 
	 * @param statement    - prepared statement to use
	 * @param queue        - queue containing batch data
	 * @param flush        - should the queue be flushed
	 * @param process      - callback to execute the prepared statement
	 * @param errorHandler - error handler
	 */
	private void processQueue(PreparedStatement statement, Queue<long[]> queue, boolean flush,
			StatementConsumer process, Consumer<SQLException> errorHandler) {
		try {
			// Check if the queue contains any items along with race conditions
			if (queue.size() > 0)
				synchronized (queue) {
					if (queue.size() > 0) {

						int initialSize = queue.size();
						int size = 0;
						long[] item = null;

						// Loop until we finish all items or the threshold is reached
						while ((flush || size < batchData.threshold) && (item = queue.poll()) != null) {
							statement.setLong(1, item[0]);
							statement.setLong(2, item[1]);
							statement.setLong(3, item[2]);
							statement.addBatch();
							size++;
						}

						// Pass data to callback
						process.accept(statement, size, initialSize);
					}
				}
		} catch (SQLException e) {
			if (errorHandler != null)
				errorHandler.accept(e);
			else
				e.printStackTrace();
		}
	}

	/**
	 * Start the worker thread.
	 * 
	 * @throws UnsupportedOperationException If the worker thread is already running
	 */
	public void start() {
		if (thread.isAlive())
			throw new UnsupportedOperationException("Worker is already running");

		thread.start();

		while (!running.get()) {
			Thread.onSpinWait();
		}
	}

	@Override
	public synchronized void close() {
		logger.trace("Sending worker interupt");
		flush.set(true);
		running.set(false);
		thread.interrupt();

		try {
			thread.join();
		} catch (Exception e) {}
	}

	@Override
	public void flush() {
		synchronized (batchData) {
			logger.debug("Flagging worker for flushing");
			flush.set(true);
			batchData.notify();
		}
	}

	@Override

	public RoleBatchWorker addMemberRole(Member member, Role role) {
		addToBatch(batchData.insertQueue,
				new long[] { member.getIdLong(), member.getGuild().getIdLong(), role.getIdLong() });
		return this;
	}

	@Override

	public RoleBatchWorker removeMemberRole(Member member, Role role) {
		addToBatch(batchData.removeQueue,
				new long[] { member.getIdLong(), member.getGuild().getIdLong(), role.getIdLong() });
		return this;
	}

	/**
	 * Add a batch of data to be processed. If upon adding the data, the batch
	 * threshold is reached, then the worker thread will be notified to process
	 * {@code threshold} amount of items.
	 * 
	 * @param queue - queue to add {@code data} to
	 * @param data  - data to be inserted to {@code queue}
	 */
	private void addToBatch(Queue<long[]> queue, long[] data) {
		synchronized (batchData) {
			queue.add(data);
			if (!processing.get() && batchData.thresholdReached()) {
				processing.set(true);
				batchData.notify();
			}
		}
	}

	/**
	 * Record used to hold all data needed for {@link BatchWorker}.
	 * 
	 * @author Ashley
	 *
	 * @param <T> - Any class that extends a {@link Queue}
	 */
	public record BatchData<T extends Queue<long[]>>(Connection source, T insertQueue, T removeQueue,
			Supplier<String> insertStatement, Supplier<String> removeStatement, int threshold) {

		/**
		 * Get the total number of items held by this instance.
		 * 
		 * @return The sum of both queues
		 */
		public int size() {
			return insertQueue.size() + removeQueue.size();
		}

		/**
		 * Checks if there are no items contained by this instance.
		 * 
		 * @return Returns {@code true} if {@link #size()} is equal to {@code 0}.
		 */
		public boolean isEmpty() {
			return size() == 0;
		}

		/**
		 * Checks if the number of items contained within this instance has reached the
		 * threshold.
		 * 
		 * @return Returns {@code true} when {@link #size()} is greater than or equal to
		 *         the threshold
		 */
		public boolean thresholdReached() {
			return size() >= threshold;
		}
	}

	@FunctionalInterface
	private interface StatementConsumer {
		public void accept(PreparedStatement statement, int batchSize, int queueSize) throws SQLException;
	}
}
