package com.alipay.sofa.jraft;

import java.nio.ByteBuffer;

/**
 * Iterator over a batch of committed tasks.
 * @see StateMachine#onApply(Iterator)
 *
 * @author boyan (boyan@alibaba-inc.com)
 *
 * 2018-Apr-03 3:20:15 PM
 */
public interface Iterator extends java.util.Iterator<ByteBuffer> {

    /**
     * Return the data whose content is the same as what was passed to
     * Node#apply(Task) in the leader node.
     */
    ByteBuffer getData();

    /**
     * Return a unique and monotonically increasing identifier of the current task:
     * - Uniqueness guarantees that committed tasks in different peers with
     *    the same index are always the same and kept unchanged.
     * - Monotonicity guarantees that for any index pair i, j (i < j), task
     *    at index |i| must be applied before task at index |j| in all the
     *    peers from the group.
     */
    long getIndex();

    /**
     * Returns the term of the leader which to task was applied to.
     */
    long getTerm();

    /**
     * If done() is non-NULL, you must call done()->Run() after applying this
     * task no matter this operation succeeds or fails, otherwise the
     * corresponding resources would leak.
     *
     * If this task is proposed by this Node when it was the leader of this
     * group and the leadership has not changed before this point, done() is
     * exactly what was passed to Node#apply(Task) which may stand for some
     * continuation (such as respond to the client) after updating the
     * StateMachine with the given task. Otherwise done() must be NULL.
     * */
    Closure done();

    /**
     * Commit state machine. After this invocation, we will consider that
     * the state machine promises the last task is already applied successfully and can't be rolled back.
     * @since 1.3.11
     */
    boolean commit();

    /**
     * Commit state machine, then try to save a snapshot with current log applied if commit successfully.
     * @see Node#snapshotSync(Closure)
     * @see #commit()
     * @param done Invoked when the snapshot finishes, describing the detailed result.
     * @since 1.3.11
     */
    void commitAndSnapshotSync(Closure done);

    /**
     * Invoked when some critical error occurred. And we will consider the last
     * |ntail| tasks (starting from the last iterated one) as not applied. After
     * this point, no further changes on the StateMachine as well as the Node
     * would be allowed and you should try to repair this replica or just drop it.
     *
     * @param ntail the number of tasks (starting from the last iterated one)  considered as not to be applied.
     * @param st    Status to describe the detail of the error.
     */
    void setErrorAndRollback(final long ntail, final Status st);
}
