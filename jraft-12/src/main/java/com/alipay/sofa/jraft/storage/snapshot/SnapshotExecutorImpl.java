package com.alipay.sofa.jraft.storage.snapshot;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.FSMCaller;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.closure.LoadSnapshotClosure;
import com.alipay.sofa.jraft.closure.SaveSnapshotClosure;
import com.alipay.sofa.jraft.core.NodeImpl;
import com.alipay.sofa.jraft.entity.EnumOutter;
import com.alipay.sofa.jraft.entity.RaftOutter;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.option.SnapshotCopierOptions;
import com.alipay.sofa.jraft.option.SnapshotExecutorOptions;
import com.alipay.sofa.jraft.rpc.RpcRequestClosure;
import com.alipay.sofa.jraft.rpc.RpcRequests;
import com.alipay.sofa.jraft.storage.LogManager;
import com.alipay.sofa.jraft.storage.SnapshotExecutor;
import com.alipay.sofa.jraft.storage.SnapshotStorage;
import com.alipay.sofa.jraft.storage.snapshot.local.LocalSnapshotStorage;
import com.alipay.sofa.jraft.util.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;



/**
 * @课程描述:从零带你写框架系列中的课程，整个系列包含netty，xxl-job，rocketmq，nacos，sofajraft，spring，springboot，disruptor，编译器，虚拟机等等。
 * @author：陈清风扬，个人微信号：chenqingfengyangjj。
 * @date:2023/12/19
 * @方法描述：日志快照组件，在第八版本代码中，我只引入了快照生成的有关代码，跟随者节点从领导者节点安装快照的操作并没有实现
 * 这个操作会在第九版本实现，所以我也没有把多余的代码删除，而是直接注释了，这样下个版本就可以直接使用了
 */
public class SnapshotExecutorImpl implements SnapshotExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(SnapshotExecutorImpl.class);

    private final Lock lock = new ReentrantLock();
    //最新快照的任期
    private long lastSnapshotTerm;
    //最新快照记录的日志索引，也就是在生成快照的时候，应用到那条日志了，这条日志是最新的
    //那么就会把这个日志索引记录在这里
    private long lastSnapshotIndex;
    //节点当前的任期
    private long term;
    //当前执行器是否正在保存快照
    private volatile boolean savingSnapshot;
    //是否正在加载快照
    private volatile boolean loadingSnapshot;
    //快照执行器是否已停止工作
    private volatile boolean stopped;
    //快照存储器，这个存储器的作用就是生成快照以及加载快照
    private SnapshotStorage snapshotStorage;
    //快照复制器，这个是用来从领导者辅助快照的，在第八本版还用不到
    //private SnapshotCopier curCopier;
    //状态机组件，快照最终也是要作用到业务层的，比如跟随者节点从领导者安装了快照，肯定需要把快照的内容作用到状态机上
    private FSMCaller fsmCaller;
    //节点本身
    private NodeImpl node;
    //日志管理器
    private LogManager logManager;
    //正在下载的快照
    private final AtomicReference<DownloadingSnapshot> downloadingSnapshot = new AtomicReference<>(null);
    //正在加载的快照的元数据
    private RaftOutter.SnapshotMeta loadingSnapshotMeta;
    //正在执行的任务的数量
    private final CountDownEvent runningJobs = new CountDownEvent();
    //快照复制器
    private SnapshotCopier curCopier;



    static class DownloadingSnapshot {
        RpcRequests.InstallSnapshotRequest request;
        RpcRequests.InstallSnapshotResponse.Builder responseBuilder;
        RpcRequestClosure done;

        public DownloadingSnapshot(final RpcRequests.InstallSnapshotRequest request,
                                   final RpcRequests.InstallSnapshotResponse.Builder responseBuilder, final RpcRequestClosure done) {
            super();
            this.request = request;
            this.responseBuilder = responseBuilder;
            this.done = done;
        }
    }


    public long getLastSnapshotTerm() {
        return this.lastSnapshotTerm;
    }


    public long getLastSnapshotIndex() {
        return this.lastSnapshotIndex;
    }



    /**
     * @课程描述:从零带你写框架系列中的课程，整个系列包含netty，xxl-job，rocketmq，nacos，sofajraft，spring，springboot，disruptor，编译器，虚拟机等等。
     * @author：陈清风扬，个人微信号：chenqingfengyangjj。
     * @date:2023/12/20
     * @方法描述：保存快照的操作完成后要回调的方法，就封装在这个内部类中
     */
    private class SaveSnapshotDone implements SaveSnapshotClosure {

        SnapshotWriter writer;
        Closure done;
        RaftOutter.SnapshotMeta meta;

        public SaveSnapshotDone(final SnapshotWriter writer, final Closure done, final RaftOutter.SnapshotMeta meta) {
            super();
            this.writer = writer;
            this.done = done;
            this.meta = meta;
        }

        //快照生成完成后，回调这个方法
        @Override
        public void run(final Status status) {
            ThreadPoolsFactory.runInThread(getNode().getGroupId(), () -> continueRun(status));
        }
        //进一步回调到这个方法
        void continueRun(final Status st) {
            //这个是最重要的方法，在这个方法中，会对日志进行处理
            final int ret = onSnapshotSaveDone(st, this.meta, this.writer);
            if (ret != 0 && st.isOk()) {
                st.setError(ret, "node call onSnapshotSaveDone failed");
            }
            if (this.done != null) {
                ThreadPoolsFactory.runClosureInThread(getNode().getGroupId(), this.done, st);
            }
        }

        @Override
        public SnapshotWriter start(final RaftOutter.SnapshotMeta meta) {
            this.meta = meta;
            this.writer.setCurrentMeta(meta);
            return this.writer;
        }
    }


    private class InstallSnapshotDone implements LoadSnapshotClosure {

        SnapshotReader reader;

        public InstallSnapshotDone(final SnapshotReader reader) {
            super();
            this.reader = reader;
        }

        @Override
        public void run(final Status status) {
            onSnapshotLoadDone(status);
        }

        @Override
        public SnapshotReader start() {
            return this.reader;
        }
    }


    private class FirstSnapshotLoadDone implements LoadSnapshotClosure {

        SnapshotReader reader;
        CountDownLatch eventLatch;
        Status         status;

        public FirstSnapshotLoadDone(final SnapshotReader reader) {
            super();
            this.reader = reader;
            this.eventLatch = new CountDownLatch(1);
        }

        @Override
        public void run(final Status status) {
            this.status = status;
            onSnapshotLoadDone(this.status);
            this.eventLatch.countDown();
        }

        public void waitForRun() throws InterruptedException {
            this.eventLatch.await();
        }

        @Override
        public SnapshotReader start() {
            return this.reader;
        }

    }


    /**
     * @课程描述:从零带你写框架系列中的课程，整个系列包含netty，xxl-job，rocketmq，nacos，sofajraft，spring，springboot，disruptor，编译器，虚拟机等等。
     * @author：陈清风扬，个人微信号：chenqingfengyangjj。
     * @date:2023/12/20
     * @方法描述：快照执行器初始化的方法
     */
    @Override
    public boolean init(final SnapshotExecutorOptions opts) {
        //对存储快照文件的uri判空
        if (StringUtils.isBlank(opts.getUri())) {
            LOG.error("Snapshot uri is empty.");
            return false;
        }
        //给成员变量赋值
        this.logManager = opts.getLogManager();
        this.fsmCaller = opts.getFsmCaller();
        this.node = opts.getNode();
        this.term = opts.getInitTerm();
        //快照存储器在这里创建了，得到的是一个LocalSnapshotStorage对象
        //并且在这里也把快照存储路径传递给了快照存储器
        this.snapshotStorage = this.node.getServiceFactory().createSnapshotStorage(opts.getUri(),
                this.node.getRaftOptions());
        //设置远程复制快照之前是否过滤重复文件的变量，这个在第九版本会用到
        //默认为false，也就是不检测
        if (opts.isFilterBeforeCopyRemote()) {
            this.snapshotStorage.setFilterBeforeCopyRemote();
        }
        //设置快照限流器
        if (opts.getSnapshotThrottle() != null) {
            //快照限流器默认为null，也就是不设置
            this.snapshotStorage.setSnapshotThrottle(opts.getSnapshotThrottle());
        }
        //在这里初始化快照存储器
        if (!this.snapshotStorage.init(null)) {
            LOG.error("Fail to init snapshot storage.");
            return false;
        }
        //设置本地快照的服务器地址，其实就是当前节点的地址
        if (this.snapshotStorage instanceof LocalSnapshotStorage) {
            final LocalSnapshotStorage tmp = (LocalSnapshotStorage) this.snapshotStorage;
            if (!tmp.hasServerAddr()) {
                tmp.setServerAddr(opts.getAddr());
            }
        }
        //得到快照读取器
        final SnapshotReader reader = this.snapshotStorage.open();
        if (reader == null) {
            return true;
        }
        //快照读取器加载一下本地快照的元数据
        this.loadingSnapshotMeta = reader.load();
        if (this.loadingSnapshotMeta == null) {
            LOG.error("Fail to load meta from {}.", opts.getUri());
            //加载失败则关闭快照读取器
            Utils.closeQuietly(reader);
            return false;
        }
        LOG.info("Loading snapshot, meta={}.", this.loadingSnapshotMeta);
        //设置正在加载快照文件的标志
        this.loadingSnapshot = true;
        //增加正在运行的工作数量
        this.runningJobs.incrementAndGet();
        //创建回调对象，这个回调对象封装的方法会在快照加载完成后被回调
        final FirstSnapshotLoadDone done = new FirstSnapshotLoadDone(reader);
        //在这里状态机组件开始加载快照了，到这里大家应该也能意识到了，如果节点启动的时候，本地已经有快照文件了
        //那么状态机就要把快照文件中记录的数据都应用到状态机上，这就是所谓的加载快照文件
        //这就是通过加载快照文件，快速使节点回复到宕机之前状态的最好方法
        Requires.requireTrue(this.fsmCaller.onSnapshotLoad(done));
        try {
            //在这里同步等待快照加载完成
            done.waitForRun();
        } catch (final InterruptedException e) {
            LOG.warn("Wait for FirstSnapshotLoadDone run is interrupted.");
            Thread.currentThread().interrupt();
            return false;
        } finally {
            //加载完成后关闭快照读取器
            Utils.closeQuietly(reader);
        }
        //走到这里就要判断一下快照是否加载失败了，如果加载失败就返回false
        if (!done.status.isOk()) {
            LOG.error("Fail to load snapshot from {}, FirstSnapshotLoadDone status is {}.", opts.getUri(), done.status);
            return false;
        }
        //加载成功返回true
        return true;
    }

    //关闭快照执行器
    @Override
    public void shutdown() {
        long savedTerm;
        this.lock.lock();
        try {
            savedTerm = this.term;
            this.stopped = true;
        } finally {
            this.lock.unlock();
        }
        interruptDownloadingSnapshots(savedTerm);
    }

    @Override
    public NodeImpl getNode() {
        return this.node;
    }


    /**
     * @课程描述:从零带你写框架系列中的课程，整个系列包含netty，xxl-job，rocketmq，nacos，sofajraft，spring，springboot，disruptor，编译器，虚拟机等等。
     * @author：陈清风扬，个人微信号：chenqingfengyangjj。
     * @date:2023/12/19
     * @方法描述：异步生成快照的入口方法
     */
    @Override
    public void  doSnapshot(Closure done) {
        this.doSnapshot(done, false);
    }

    /**
     * @课程描述:从零带你写框架系列中的课程，整个系列包含netty，xxl-job，rocketmq，nacos，sofajraft，spring，springboot，disruptor，编译器，虚拟机等等。
     * @author：陈清风扬，个人微信号：chenqingfengyangjj。
     * @date:2023/12/19
     * @方法描述：同步生成快照的入口方法
     */
    @Override
    public void doSnapshotSync(Closure done) {
        this.doSnapshot(done, true);
    }


    /**
     * @课程描述:从零带你写框架系列中的课程，整个系列包含netty，xxl-job，rocketmq，nacos，sofajraft，spring，springboot，disruptor，编译器，虚拟机等等。
     * @author：陈清风扬，个人微信号：chenqingfengyangjj。
     * @date:2023/12/19
     * @方法描述：生成快照的方法
     */
    private void doSnapshot(final Closure done, boolean sync) {
        boolean doUnlock = true;
        //上锁
        this.lock.lock();
        try {
            //判断当前快照执行器是否已经停止工作
            if (this.stopped) {
                //如果已经停止工作，那就直接执行回调方法，如果是快照定时器定时生成快照，这里的这个done其实为null，具体逻辑可以从NodeImpl
                //的handleSnapshotTimeout方法查看
                ThreadPoolsFactory.runClosureInThread(getNode().getGroupId(), done, new Status(RaftError.EPERM,
                        "Is stopped."));
                return;
            }
            //这里我要解释一下，为什么同步生成快照时，必须要在状态机的线程中才行，其实源码中默认快照都是异步生成的
            //源码中也没有快照同步生成的场景。说到底，快照生成本身是业务层要做的操作，快照生成并不是对sofajraft的日志系统做什么操作
            //而是基于业务层的数据，生成快照文件，比如业务层是一个kv数据库，那么生成快照的时候就会把数据库中的最新数据都记录到快照文件中
            //跟随者节点加载快照的时候，也会把快照文件应用到业务层的状态机。这样一来，如果时同步生成快照，那就需要在最新的日志被应用后，在状态机中直接生成最新的快照，也就是在应用日志的时候定义一个
            //回调方法，当日志应用成功后，直接执行这个回调方法，在回调方法中生成快照
            //这时候就会在状态机中回调当前的doSnapshot方法，同步生成快照，生成的快照是最新的数据，并且生成快照的时候会阻塞状态机，如果有新的日志需要应用到状态机
            //这时候就不会应用，因为状态机正在因为快照生成而阻塞
            //如果是异步生成快照，快照生成的时候，会在业务层的状态机中使用新的线程来生成快照，快照生成是基于业务层的某一个瞬间，生成的数据可能不是最新的，并且生成快照时
            //状态机依然可以把日志继续应用到状态机上
            //这里就是判断，如果是同步生成，那就看看执行当前方法的线程是不是状态机的线程，如果不是，则直接抛出异常
            if (sync && !this.fsmCaller.isRunningOnFSMThread()) {
                ThreadPoolsFactory.runClosureInThread(getNode().getGroupId(), done, new Status(RaftError.EACCES,
                        "trigger snapshot synchronously out of StateMachine's callback methods"));
                throw new IllegalStateException(
                        "You can't trigger snapshot synchronously out of StateMachine's callback methods.");

            }
            //判断执行器是否正在下载另一个快照，如果正在下载快照，就直接退出，不执行安装快照的操作
            if (this.downloadingSnapshot.get() != null) {
                ThreadPoolsFactory.runClosureInThread(getNode().getGroupId(), done, new Status(RaftError.EBUSY,
                        "Is loading another snapshot."));
                return;
            }
            //如果执行器正在保存一个快照，也直接退出该方法
            if (this.savingSnapshot) {
                ThreadPoolsFactory.runClosureInThread(getNode().getGroupId(), done, new Status(RaftError.EBUSY,
                        "Is saving another snapshot."));
                return;
            }
            //这里判断一下，如果触发快照生成器定时任务时，发现状态机最后应用的日志索引和最后记录的快照的日志索引一样，这就说明
            //在这段时间内状态机没有什么变化，没有应用新的日志，所以也就没必要生成快照，直接退出即可
            if (this.fsmCaller.getLastAppliedIndex() == this.lastSnapshotIndex) {
                doUnlock = false;
                this.lock.unlock();
                //这里的这个方法是清除日志缓冲区中的日志，这里我也要解释一下，快照生成后清除日志的操作并不是真的在这里进行
                //我想解释的是快照生成后旧日志清楚的时机，这个逻辑其实是在LogManagerImpl的setSnapshot方法中
                //每次生成快照后，并不会立即就把对应的旧日志全部删除，而是删除前一次快照生成后对应的旧日志，换句话说，每一次生成快照文件，其对应的旧日志文件都会在下一次快照生成时被删除
                //为什么不在快照生成后立刻就把已经被记录到快照中的日志全清楚了呢？这里还考虑到可能有的节点是从故障中恢复过来的
                //也可能节点本身并不落后领导者多少，可能只落后几十条日志，但是领导者在生成快照后直接把旧的日志全清楚了
                //那么这个跟随者就不得不从领导者安装快照了，快照文件可能会很大，这样就难免有些麻烦了，所以在生成快照后并不会立刻把旧的日志清除
                //但是，回到我们上面所说的，如果在快照定时器还没触发的这段时间中一直没有日志被应用到状态机上，也就是说下一次不会进行快照生成的操作
                //那怎么删除上一次快照生成留下的旧的日志文件呢？现在大家应该清楚了，就是在这里删除的，这也就是下面这行代码的意义所在
                this.logManager.clearBufferedLogs();
                //执行回调方法
                ThreadPoolsFactory.runClosureInThread(getNode().getGroupId(), done);
                return;
            }
            //这里计算的是最后一次生成快照时记录的应用的日志索引距现在状态机应用的最新的日志的索引的差值
            final long distance = this.fsmCaller.getLastAppliedIndex() - this.lastSnapshotIndex;
            //如果差值小于用户配置的，就不必生成快照，比如距离上次生成快照之后，状态机仅仅应用了十几条日志，这就没必要生成快照了
            //这个差值是可以由用户配置的，默认是0
            if (distance < this.node.getOptions().getSnapshotLogIndexMargin()) {
                if (this.node != null) {
                    LOG.debug(
                            "Node {} snapshotLogIndexMargin={}, distance={}, so ignore this time of snapshot by snapshotLogIndexMargin setting.",
                            this.node.getNodeId(), distance, this.node.getOptions().getSnapshotLogIndexMargin());
                }
                doUnlock = false;
                this.lock.unlock();
                //执行回调方法
                ThreadPoolsFactory.runClosureInThread(
                                getNode().getGroupId(),
                                done,
                                new Status(RaftError.ECANCELED,
                                        "The snapshot index distance since last snapshot is less than NodeOptions#snapshotLogIndexMargin, canceled this task."));
                return;
            }
            //创建一个快照写入器
            final SnapshotWriter writer = this.snapshotStorage.create();
            if (writer == null) {
                ThreadPoolsFactory.runClosureInThread(getNode().getGroupId(), done, new Status(RaftError.EIO,
                        "Fail to create writer."));
                reportError(RaftError.EIO.getNumber(), "Fail to create snapshot writer.");
                return;
            }
            //设置正在保存快照的标志
            this.savingSnapshot = true;
            //创建一个快照生成完成后的回调对象，快照生成完毕后要回调这个对象中的方法
            final SaveSnapshotDone saveSnapshotDone = new SaveSnapshotDone(writer, done, null);
            //如果是同步生成快照，就直接调用状态机组件的方法，生成快照
            //在生成快照的时候，会使用状态机的线程来执行这个操作
            if (sync) {
                this.fsmCaller.onSnapshotSaveSync(saveSnapshotDone);
            } else {
                //这里就是异步生成快照的操作
                if (!this.fsmCaller.onSnapshotSave(saveSnapshotDone)) {
                    ThreadPoolsFactory.runClosureInThread(getNode().getGroupId(), done, new Status(RaftError.EHOSTDOWN,
                            "The raft node is down."));
                    return;
                }
            }
            //记录正在执行的任务数量
            this.runningJobs.incrementAndGet();
        } finally {
            if (doUnlock) {
                this.lock.unlock();
            }
        }
    }



    /**
     * @课程描述:从零带你写框架系列中的课程，整个系列包含netty，xxl-job，rocketmq，nacos，sofajraft，spring，springboot，disruptor，编译器，虚拟机等等。
     * @author：陈清风扬，个人微信号：chenqingfengyangjj。
     * @date:2023/12/20
     * @方法描述：当快照生成完成之后，要回调这个方法
     */
    int onSnapshotSaveDone(final Status st, final RaftOutter.SnapshotMeta meta, final SnapshotWriter writer) {
        int ret;
        this.lock.lock();
        try {
            //得到保存快照后的执行结果
            ret = st.getCode();
            //如果快照生成成功了
            if (st.isOk()) {
                //在这里判断一下，快照元数据中记录的最后一条日志的索引是否小于等于快照生成前的日志索引
                //如果小于或者等于，就设置错误码，表示生成的快照已经过期，但这里怎么会出现这种情况呢？
                //刚刚生成的快照竟然过期了，除非在本地生成快照的时候，接收到领导者传过来的最新的快照文件了
                //也就是执行了安装快照的操作，安装完快照之后，lastSnapshotIndex这个成员变量就是最新的了
                //但是在第九版本中大家就会看到这样一种逻辑，就是在跟随者节点安装快照的时候会判断当前是否正在生成快照
                //如果正在生成快照，是不会执行安装快照的操作的，而且安装快照的过程中，跟随者节点也是不会接收领导者传输过来的日志
                if (meta.getLastIncludedIndex() <= this.lastSnapshotIndex) {
                    //设置快照过期错误状态码
                    ret = RaftError.ESTALE.getNumber();
                    if (this.node != null) {
                        LOG.warn("Node {} discards an stale snapshot lastIncludedIndex={}, lastSnapshotIndex={}.",
                                this.node.getNodeId(), meta.getLastIncludedIndex(), this.lastSnapshotIndex);
                    }
                    writer.setError(RaftError.ESTALE, "Installing snapshot is older than local snapshot");
                }
            }
        } finally {
            this.lock.unlock();
        }
        //走到这里意味着快照生成成功了
        if (ret == 0) {
            //保存本次快照的元数据信息
            if (!writer.saveMeta(meta)) {
                LOG.warn("Fail to save snapshot {}.", writer.getPath());
                ret = RaftError.EIO.getNumber();
            }
        } else {
            if (writer.isOk()) {
                writer.setError(ret, "Fail to do snapshot.");
            }
        }
        try {
            //关闭快照写入器，这里需要注意一下，在关闭的过程中，还会执行本地快照存储器的关闭，在关闭的时候
            //会把快照元数据写入磁盘，把快照文件从临时文件移动到正式文件中
            writer.close();
        } catch (final IOException e) {
            LOG.error("Fail to close writer", e);
            ret = RaftError.EIO.getNumber();
        }
        boolean doUnlock = true;
        this.lock.lock();
        try {
            //这里又是判断快照是否保存成功
            if (ret == 0) {
                //如果这里也成功了，就更新快照应用的最后一条日志的索引和任期
                this.lastSnapshotIndex = meta.getLastIncludedIndex();
                this.lastSnapshotTerm = meta.getLastIncludedTerm();
                doUnlock = false;
                this.lock.unlock();
                //在这里调用日志管理器的方法，清除旧日志，设置lastSnapshotId的值等等
                this.logManager.setSnapshot(meta);
                doUnlock = true;
                this.lock.lock();
            }
            if (ret == RaftError.EIO.getNumber()) {
                reportError(RaftError.EIO.getNumber(), "Fail to save snapshot.");
            }
            //生成快照的操作已经完成了，把正在生成快照的标志设置为false
            this.savingSnapshot = false;
            //减少正在执行的任务数量
            this.runningJobs.countDown();
            return ret;
        } finally {
            if (doUnlock) {
                this.lock.unlock();
            }
        }
    }


    private void onSnapshotLoadDone(final Status st) {
        //定义一个快照下载任务对象
        DownloadingSnapshot m;
        boolean doUnlock = true;
        this.lock.lock();
        try {
            //因为刚刚把快照数据应用到状态机上，所以这里肯定还是正在加载快照的状态
            Requires.requireTrue(this.loadingSnapshot, "Not loading snapshot");
            //获取当前正在进行的下载快照的任务
            m = this.downloadingSnapshot.get();
            //这里判断快照是成功应用到状态机上了
            if (st.isOk()) {
                //如果成功则更新最后应用的日志的索引
                this.lastSnapshotIndex = this.loadingSnapshotMeta.getLastIncludedIndex();
                //更新最后应用的日志的人气
                this.lastSnapshotTerm = this.loadingSnapshotMeta.getLastIncludedTerm();
                doUnlock = false;
                this.lock.unlock();
                //更新日志管理器中的快照元数据
                this.logManager.setSnapshot(this.loadingSnapshotMeta);
                doUnlock = true;
                this.lock.lock();
            }//下面是记录日志的操作
            final StringBuilder sb = new StringBuilder();
            if (this.node != null) {
                sb.append("Node ").append(this.node.getNodeId()).append(" ");
            }
            sb.append("onSnapshotLoadDone, ").append(this.loadingSnapshotMeta);
            LOG.info(sb.toString());
            doUnlock = false;
            this.lock.unlock();
            //在安装快照之后检查节点配置信息是否发生了变化
            if (this.node != null) {
                this.node.updateConfigurationAfterInstallingSnapshot();
            }
            doUnlock = true;
            this.lock.lock();
            //快照也应用完了，可以把正在架子啊快照的标志设置为false了
            this.loadingSnapshot = false;
            //重置正在进行的下载快照的任务
            this.downloadingSnapshot.set(null);

        } finally {
            if (doUnlock) {
                this.lock.unlock();
            }
        }
        if (m != null) {
            //再次判断快照数据是否应用成功了
            if (!st.isOk()) {
                //不成功则执行回调方法
                m.done.run(st);
            } else {
                //成功则直接回复领导者一个成功响应
                m.responseBuilder.setSuccess(true);
                m.done.sendResponse(m.responseBuilder.build());
            }
        }
        //将正在执行的任务减1，因为已经完成了一个安装快照的任务了
        this.runningJobs.countDown();
    }



    /**
     * @课程描述:从零带你写框架系列中的课程，整个系列包含netty，xxl-job，rocketmq，nacos，sofajraft，spring，springboot，disruptor，编译器，虚拟机等等。
     * @author：陈清风扬，个人微信号：chenqingfengyangjj。
     * @date:2024/3/13
     * @方法描述：安装快照的方法
     */
    public void installSnapshot(final RpcRequests.InstallSnapshotRequest request,
                                final RpcRequests.InstallSnapshotResponse.Builder response,
                                final RpcRequestClosure done) {
        //从请求中得到要安装的快照文件的元数据
        final RaftOutter.SnapshotMeta meta = request.getMeta();
        //创建一个正在下载快照的任务
        final DownloadingSnapshot ds = new DownloadingSnapshot(request, response, done);
        //接下来，在该方法中当前跟随者节点就会从领导者节点把快照数据下载下来
        if (!registerDownloadingSnapshot(ds)) {
            LOG.warn("Fail to register downloading snapshot.");
            //直接退出该方法，失败响应已经在registerDownloadingSnapshot方法中回复给领导者了
            return;
        }
        //检验快照复制器是否为空
        Requires.requireNonNull(this.curCopier, "curCopier");
        try {
            //等待快照复制器将快照远程下载完毕，直到下载完毕，程序才能继续向下运行
            this.curCopier.join();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Install snapshot copy job was canceled.");
            return;
        }
        //到这里就意味着快照已经下载完成了，可以加载到当前的跟随者节点中了
        loadDownloadingSnapshot(ds, meta);
    }



    //加载快照文件中的数据到状态机的方法
    void loadDownloadingSnapshot(final DownloadingSnapshot ds, final RaftOutter.SnapshotMeta meta) {
        //定义一个快照读取器
        SnapshotReader reader;
        // 获取锁以保护快照的加载过程
        this.lock.lock();
        try {
            //判断本次下载快照的任务就是之前保存的那个，这说明快照下载的操作没有被打断或者取消
            //是顺利完成的
            if (ds != this.downloadingSnapshot.get()) {
                return;
            }
            //校验快照复制器不为空
            Requires.requireNonNull(this.curCopier, "curCopier");
            //获得快照读取器，注意，这里获取的快照读取器就可以读取从领导者复制到本地的最新的快照文件了
            //因为当快照复制器把领导者快照下载到本地后，快照存储器中的快照路径就被更新了，这时候通过快照复制器
            //去得到快照读取器，实际上是通过快照复制器内部的快照存储器得到的快照读取器，快照路径已经跟新
            //所以得到的这个快照读取器就可以直接读取最新的快照文件
            reader = this.curCopier.getReader();
            if (!this.curCopier.isOk()) {
                //判断快照复制器在工作期间是否出现问题了
                if (this.curCopier.getCode() == RaftError.EIO.getNumber()) {
                    reportError(this.curCopier.getCode(), this.curCopier.getErrorMsg());
                }
                //如果出现问题了就关闭快照读取器
                Utils.closeQuietly(reader);
                //执行回调方法
                ds.done.run(this.curCopier);
                //关闭快照复制器
                Utils.closeQuietly(this.curCopier);
                this.curCopier = null;
                this.downloadingSnapshot.set(null);
                this.runningJobs.countDown();
                return;
            }
            //下面都是释放资源的操作
            Utils.closeQuietly(this.curCopier);
            this.curCopier = null;
            if (reader == null || !reader.isOk()) {
                Utils.closeQuietly(reader);
                this.downloadingSnapshot.set(null);
                ds.done.sendResponse(RpcFactoryHelper
                        .responseFactory()
                        .newResponse(RpcRequests.InstallSnapshotResponse.getDefaultInstance(), RaftError.EINTERNAL,
                                "Fail to copy snapshot from %s", ds.request.getUri()));
                this.runningJobs.countDown();
                return;
            }
            //走到这里说明程序没什么问题，那就可以直接加载快照了
            //注意，这里加载快照的意思就是把从领导者复制过来的快照加载到内存中，然后应用到状态机上
            this.loadingSnapshot = true;
            //给快照元数据复制
            this.loadingSnapshotMeta = meta;
        } finally {
            this.lock.unlock();
        }
        //创建一个回调对象，该对象中的run方法会在当前节点把快照数据应用到状态机上后回调
        final InstallSnapshotDone installSnapshotDone = new InstallSnapshotDone(reader);
        //把快照数据应用到状态机上
        if (!this.fsmCaller.onSnapshotLoad(installSnapshotDone)) {
            LOG.warn("Fail to call fsm onSnapshotLoad.");
            //应用失败执行回调方法
            installSnapshotDone.run(new Status(RaftError.EHOSTDOWN, "This raft node is down"));
        }
    }



    //注册一个复制远程快照数据的任务，这个任务是靠LocalSnapshotCopier对象来完成的
    @SuppressWarnings("all")
    boolean registerDownloadingSnapshot(final DownloadingSnapshot ds) {
        //定义一个私有变量来记录正在下载的快照任务
        DownloadingSnapshot saved = null;
        //定义一个下载快照操作成功的标志
        boolean result = true;
        //上锁
        this.lock.lock();
        try {
            //如果快照执行器已经停止工作，就返回给领导者一个错误响应
            //注意，大家要理清楚，给跟随着节点安装快照的操作是领导者主动发起的，领导者通知跟随者节点要安装快照了
            //然后跟随者节点远程下载领导者的快照文件，但是当跟随者节点内部没有启动快照执行器的话是无法下载的，所以要给领导者回复一个错误响应
            //让领导者知道跟随者无法下载快照文件
            if (this.stopped) {
                LOG.warn("Register DownloadingSnapshot failed: node is stopped.");
                ds.done.sendResponse(RpcFactoryHelper //
                                .responseFactory() //
                                .newResponse(RpcRequests.InstallSnapshotResponse.getDefaultInstance(), RaftError.EHOSTDOWN,
                                        "Node is stopped."));
                return false;
            }
            //如果跟随者节点正在生成自己的快照，也回复给领导着一个错误响应
            //响应状态为跟随者节点目前繁忙
            if (this.savingSnapshot) {
                LOG.warn("Register DownloadingSnapshot failed: is saving snapshot.");
                ds.done.sendResponse(RpcFactoryHelper
                        .responseFactory().newResponse(RpcRequests.InstallSnapshotResponse.getDefaultInstance(), RaftError.EBUSY,
                                "Node is saving snapshot."));
                return false;
            }
            //设置响应中的任期
            ds.responseBuilder.setTerm(this.term);
            //判断请求中的任期和当前节点任期是否一致，不一致则给领导者返回一个失败响应
            if (ds.request.getTerm() != this.term) {
                LOG.warn("Register DownloadingSnapshot failed: term mismatch, expect {} but {}.", this.term,
                        ds.request.getTerm());
                ds.responseBuilder.setSuccess(false);
                ds.done.sendResponse(ds.responseBuilder.build());
                return false;
            }
            //这里的这个判断很重要，因为在集群中不只是领导者自己会生成快照文件，跟随者节点也会生成快照文件
            //lastSnapshotIndex就是当前节点生成的快照文件中应用的最后一条日志的索引
            //如果从领导者传递过来的这个lastSnapshotIndex比当前节点的lastSnapshotIndex还小，说明当前节点应用的日志比领导者传递过来的这个快照文件应用的最新日志要新
            //这就意味着领导者要给当前节点安装的快照是旧的，或者是过期的，不必安装
            //跟随者节点直接忽略这个请求，回复一个成功响应即可
            if (ds.request.getMeta().getLastIncludedIndex() <= this.lastSnapshotIndex) {
                LOG.warn(
                        "Register DownloadingSnapshot failed: snapshot is not newer, request lastIncludedIndex={}, lastSnapshotIndex={}.",
                        ds.request.getMeta().getLastIncludedIndex(), this.lastSnapshotIndex);
                ds.responseBuilder.setSuccess(true);
                ds.done.sendResponse(ds.responseBuilder.build());
                return false;
            }
            //获取当前正在下载的快照文件
            final DownloadingSnapshot m = this.downloadingSnapshot.get();
            //如果跟随者节点还没有开始远程下载领导者内部的快照文件
            if (m == null) {
                //就把在外层方法创建的快照下载任务设置到downloadingSnapshot中
                this.downloadingSnapshot.set(ds);
                //接下来就要开始远程下载快照，创建快照文件下载器，再没创建之前要校验一下快照文件下载器应该为null
                Requires.requireTrue(this.curCopier == null, "Current copier is not null");
                //在这里远程下载快照文件，返回一个快照文件下载器
                this.curCopier = this.snapshotStorage.startToCopyFrom(ds.request.getUri(), newCopierOpts());
                //如果curCopier为null，说明远程下载快照文件失败，返回领导者一个失败响应
                if (this.curCopier == null) {
                    this.downloadingSnapshot.set(null);
                    LOG.warn("Register DownloadingSnapshot failed: fail to copy file from {}.", ds.request.getUri());
                    ds.done.sendResponse(RpcFactoryHelper
                            .responseFactory()
                            .newResponse(RpcRequests.InstallSnapshotResponse.getDefaultInstance(), RaftError.EINVAL,
                                    "Fail to copy from: %s", ds.request.getUri()));
                    return false;
                }
                //增加正在运行的任务数量
                this.runningJobs.incrementAndGet();
                return true;
            }
            //程序执行到这里意味着刚才的得到的m，也就是正在执行的快照下载任务不为空，说明已经有快照正在从领导者那里下载了
            //经过判断也发现两个快照文件下载任务中封装的请求中的last_included_index也相同，这就意味着快照请求发送重复了
            if (m.request.getMeta().getLastIncludedIndex() == ds.request.getMeta().getLastIncludedIndex()) {
                saved = m;
                this.downloadingSnapshot.set(ds);
                result = true;
            } else if (m.request.getMeta().getLastIncludedIndex() > ds.request.getMeta().getLastIncludedIndex()) {
                //走到这里就是当前接收到的快照请求是旧的，m封装的快照请求是新的，可能快照请求重复发送了
                //这时候就要返回一个错误响应给领导者
                LOG.warn("Register DownloadingSnapshot failed: is installing a newer one, lastIncludeIndex={}.",
                        m.request.getMeta().getLastIncludedIndex());
                ds.done.sendResponse(RpcFactoryHelper
                        .responseFactory()
                        .newResponse(RpcRequests.InstallSnapshotResponse.getDefaultInstance(), RaftError.EINVAL,
                                "A newer snapshot is under installing"));
                return false;
            } else {
                //如果走到这里意味着接收到的请求中的快照是新的，但这就意味着当前节点正在从领导者下载旧快照，所以也回复一个错误响应
                //如果当前节点正在加载快照，那么正在加载的快照一定是旧的，因为现在复制下来的一定是最新的
                if (this.loadingSnapshot) {
                    //如果正在加载旧快照，就回复一个节点繁忙的状态码
                    LOG.warn("Register DownloadingSnapshot failed: is loading an older snapshot, lastIncludeIndex={}.",
                            m.request.getMeta().getLastIncludedIndex());
                    ds.done.sendResponse(RpcFactoryHelper
                            .responseFactory()
                            .newResponse(RpcRequests.InstallSnapshotResponse.getDefaultInstance(), RaftError.EBUSY,
                                    "A former snapshot is under loading"));
                    return false;
                }//校验快照复制器不为null
                Requires.requireNonNull(this.curCopier, "curCopier");
                //取消复制快照的操作
                this.curCopier.cancel();
                LOG.warn(
                        "Register DownloadingSnapshot failed: an older snapshot is under installing, cancel downloading, lastIncludeIndex={}.",
                        m.request.getMeta().getLastIncludedIndex());
                ds.done.sendResponse(RpcFactoryHelper
                        .responseFactory()
                        .newResponse(RpcRequests.InstallSnapshotResponse.getDefaultInstance(), RaftError.EBUSY,
                                "A former snapshot is under installing, trying to cancel"));
                return false;
            }
        } finally {
            this.lock.unlock();
        }
        if (saved != null) {
            LOG.warn("Register DownloadingSnapshot failed: interrupted by retry installing request.");
            saved.done.sendResponse(RpcFactoryHelper
                    .responseFactory()
                    .newResponse(RpcRequests.InstallSnapshotResponse.getDefaultInstance(), RaftError.EINTR,
                            "Interrupted by the retry InstallSnapshotRequest"));
        }
        return result;
    }



    private SnapshotCopierOptions newCopierOpts() {
        final SnapshotCopierOptions copierOpts = new SnapshotCopierOptions();
        copierOpts.setNodeOptions(this.node.getOptions());
        copierOpts.setRaftClientService(this.node.getRpcService());
        copierOpts.setTimerManager(this.node.getTimerManager());
        copierOpts.setRaftOptions(this.node.getRaftOptions());
        copierOpts.setGroupId(this.node.getGroupId());
        return copierOpts;
    }

    @Override
    public void interruptDownloadingSnapshots(final long newTerm) {
        this.lock.lock();
        try {
            Requires.requireTrue(newTerm >= this.term);
            this.term = newTerm;
            if (this.downloadingSnapshot.get() == null) {
                return;
            }
            if (this.loadingSnapshot) {
                // We can't interrupt loading
                return;
            }
            Requires.requireNonNull(this.curCopier, "curCopier");
            this.curCopier.cancel();
            LOG.info("Trying to cancel downloading snapshot: {}.", this.downloadingSnapshot.get().request);
        } finally {
            this.lock.unlock();
        }
    }

    private void reportError(final int errCode, final String fmt, final Object... args) {
        final RaftException error = new RaftException(EnumOutter.ErrorType.ERROR_TYPE_SNAPSHOT);
        error.setStatus(new Status(errCode, fmt, args));
        this.fsmCaller.onError(error);
    }

    @Override
    public boolean isInstallingSnapshot() {
        return this.downloadingSnapshot.get() != null;
    }

    @Override
    public SnapshotStorage getSnapshotStorage() {
        return this.snapshotStorage;
    }

    @Override
    public void join() throws InterruptedException {
        this.runningJobs.await();
    }

    @Override
    public void describe(final Printer out) {
        final long _lastSnapshotTerm;
        final long _lastSnapshotIndex;
        final long _term;
        final boolean _savingSnapshot;
        final boolean _loadingSnapshot;
        final boolean _stopped;
        this.lock.lock();
        try {
            _lastSnapshotTerm = this.lastSnapshotTerm;
            _lastSnapshotIndex = this.lastSnapshotIndex;
            _term = this.term;
            _savingSnapshot = this.savingSnapshot;
            _loadingSnapshot = this.loadingSnapshot;
            _stopped = this.stopped;
        } finally {
            this.lock.unlock();
        }
        out.print("  lastSnapshotTerm: ")
                .println(_lastSnapshotTerm);
        out.print("  lastSnapshotIndex: ")
                .println(_lastSnapshotIndex);
        out.print("  term: ")
                .println(_term);
        out.print("  savingSnapshot: ")
                .println(_savingSnapshot);
        out.print("  loadingSnapshot: ")
                .println(_loadingSnapshot);
        out.print("  stopped: ")
                .println(_stopped);
    }
}
