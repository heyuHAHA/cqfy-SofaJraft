package com.alipay.sofa.jraft.rpc;

import com.alipay.sofa.jraft.error.MessageClassNotFoundException;
import com.alipay.sofa.jraft.storage.io.ProtoBufFile;
import com.alipay.sofa.jraft.util.RpcFactoryHelper;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.apache.commons.lang.SerializationException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.invoke.MethodType.methodType;


//在这个类的静态方法中，会把用户定义在资源模块下的Protobuf描述文件加载到内存中
//并且注册ProtobufSerializer序列化器
public class ProtobufMsgFactory {

    private static Map<String/* class name in proto file */, MethodHandle> PARSE_METHODS_4PROTO = new HashMap<>();
    private static Map<String/* class name in java file */, MethodHandle> PARSE_METHODS_4J = new HashMap<>();
    private static Map<String/* class name in java file */, MethodHandle> DEFAULT_INSTANCE_METHODS_4J = new HashMap<>();

    static {
        try {
            final DescriptorProtos.FileDescriptorSet descriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(ProtoBufFile.class
                    .getResourceAsStream("/raft.desc"));
            final List<Descriptors.FileDescriptor> resolveFDs = new ArrayList<>();
            final RaftRpcFactory rpcFactory = RpcFactoryHelper.rpcFactory();
            for (final DescriptorProtos.FileDescriptorProto fdp : descriptorSet.getFileList()) {

                final Descriptors.FileDescriptor[] dependencies = new Descriptors.FileDescriptor[resolveFDs.size()];
                resolveFDs.toArray(dependencies);

                final Descriptors.FileDescriptor fd = Descriptors.FileDescriptor.buildFrom(fdp, dependencies);
                resolveFDs.add(fd);
                for (final Descriptors.Descriptor descriptor : fd.getMessageTypes()) {

                    final String className = fdp.getOptions().getJavaPackage() + "."
                            + fdp.getOptions().getJavaOuterClassname() + "$" + descriptor.getName();
                    final Class<?> clazz = Class.forName(className);
                    final MethodHandle parseFromHandler = MethodHandles.lookup().findStatic(clazz, "parseFrom",
                            methodType(clazz, byte[].class));
                    final MethodHandle getInstanceHandler = MethodHandles.lookup().findStatic(clazz,
                            "getDefaultInstance", methodType(clazz));
                    PARSE_METHODS_4PROTO.put(descriptor.getFullName(), parseFromHandler);
                    PARSE_METHODS_4J.put(className, parseFromHandler);
                    DEFAULT_INSTANCE_METHODS_4J.put(className, getInstanceHandler);
                    rpcFactory.registerProtobufSerializer(className, getInstanceHandler.invoke());
                }

            }
        } catch (final Throwable t) {
            t.printStackTrace();
        }
    }

    public static void load() {
        if (PARSE_METHODS_4J.isEmpty() || PARSE_METHODS_4PROTO.isEmpty() || DEFAULT_INSTANCE_METHODS_4J.isEmpty()) {
            throw new IllegalStateException("Parse protocol file failed.");
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Message> T getDefaultInstance(final String className) {
        final MethodHandle handle = DEFAULT_INSTANCE_METHODS_4J.get(className);
        if (handle == null) {
            throw new MessageClassNotFoundException(className + " not found");
        }
        try {
            return (T) handle.invoke();
        } catch (Throwable t) {
            throw new SerializationException(t);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Message> T newMessageByJavaClassName(final String className, final byte[] bs) {
        final MethodHandle handle = PARSE_METHODS_4J.get(className);
        if (handle == null) {
            throw new MessageClassNotFoundException(className + " not found");
        }
        try {
            return (T) handle.invoke(bs);
        } catch (Throwable t) {
            throw new SerializationException(t);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Message> T newMessageByProtoClassName(final String className, final byte[] bs) {
        final MethodHandle handle = PARSE_METHODS_4PROTO.get(className);
        if (handle == null) {
            throw new MessageClassNotFoundException(className + " not found");
        }
        try {
            return (T) handle.invoke(bs);
        } catch (Throwable t) {
            throw new SerializationException(t);
        }
    }
}
