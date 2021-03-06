package com.sd.lib.proxy;

import android.content.Context;

import com.android.dx.Code;
import com.android.dx.Comparison;
import com.android.dx.FieldId;
import com.android.dx.Label;
import com.android.dx.Local;
import com.android.dx.MethodId;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * 代理工厂
 */
public class FProxyFactory
{
    private static final String DIR_NAME_DEX = "f_proxy_files";

    private Context mContext;

    public FProxyFactory(Context context)
    {
        mContext = context.getApplicationContext();
    }

    /**
     * 返回代理class保存的目录
     *
     * @return
     */
    public File getDexDir()
    {
        return mContext.getDir(DIR_NAME_DEX, Context.MODE_PRIVATE);
    }

    /**
     * 清空所有保存本地的代理class
     */
    public void clearDexFiles()
    {
        deleteFileOrDir(getDexDir());
    }

    /**
     * 创建一个代理对象
     *
     * @param clazz             要创建代理的class
     * @param methodInterceptor 方法拦截回调对象
     * @param <T>               要创建代理的class类型
     * @return 代理对象
     */
    public final <T> T newProxy(Class<T> clazz, final FMethodInterceptor methodInterceptor)
    {
        return newProxy(clazz, null, null, methodInterceptor);
    }

    /**
     * 创建一个代理对象
     *
     * @param clazz             要创建代理的class
     * @param argsClass         要调用的构造方法参数class
     * @param args              要调用的构造方法参数
     * @param methodInterceptor 方法拦截回调对象
     * @param <T>               要创建代理的class类型
     * @return 代理对象
     */
    public final <T> T newProxy(Class<T> clazz,
                                Class[] argsClass, Object[] args,
                                final FMethodInterceptor methodInterceptor)
    {
        if (methodInterceptor == null)
        {
            throw new FProxyException("methodInterceptor must not be null");
        }

        if (clazz.isInterface())
        {
            Object proxy = Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, new InvocationHandler()
            {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
                {
                    FInterceptInfo info = new FInterceptInfo(proxy, method.getName(), method.getParameterTypes());
                    info.setMethod(method);
                    return methodInterceptor.intercept(info, args);
                }
            });
            return (T) proxy;
        } else
        {
            int modifiers = clazz.getModifiers();
            if (Modifier.isFinal(modifiers))
            {
                throw new FProxyException("FProxy clazz must not be final");
            }
            if (Modifier.isPrivate(modifiers))
            {
                throw new FProxyException("FProxy clazz must not be private");
            }

            DexMakerHelper helper = new DexMakerHelper(clazz);
            makeProxyClass(helper);

            ClassLoader loader = null;
            Class classProxy = null;
            Constructor constructor = null;
            FProxyInterface proxy = null;
            try
            {
                loader = helper.getDexMaker().generateAndLoad(getClass().getClassLoader(), getDexDir());
            } catch (IOException e)
            {
                throw new FProxyException(e);
            }
            try
            {
                classProxy = loader.loadClass(helper.getProxyClassName());
            } catch (ClassNotFoundException e)
            {
                throw new FProxyException(e);
            }
            try
            {
                constructor = classProxy.getDeclaredConstructor(argsClass);
            } catch (NoSuchMethodException e)
            {
                throw new FProxyException(e);
            }
            try
            {
                proxy = (FProxyInterface) constructor.newInstance(args);
                proxy.setMethodInterceptor$FProxy$(methodInterceptor);
                return (T) proxy;
            } catch (Exception e)
            {
                throw new FProxyException(e);
            }
        }
    }

    private void makeProxyClass(DexMakerHelper helper)
    {
        // public class com/sd/model/Person$FProxy$ extends com/sd/model/Person implements FProxyInterface
        helper.declareClass(Modifier.PUBLIC, helper.getSuperClass(), FProxyInterface.class);

        // ---------- 构造方法start ----------

        helper.declareConstructors();

        // ---------- 构造方法end ----------

        // ---------- 属性start ----------
        // private FMethodInterceptor mMethodInterceptor = null;
        helper.declareField(Modifier.PRIVATE,
                FMethodInterceptor.class, FProxyInterface.FIELD_NAME_METHODINTERCEPTOR, null);
        // ---------- 属性end ----------

        // ---------- FProxyInterface接口方法start ----------
        /**
         * public void setMethodInterceptor$FProxy$(FMethodInterceptor interceptor)
         * {
         *     mMethodInterceptor = handler;
         * }
         */
        Code code = helper.declareMethod(Modifier.PUBLIC,
                Void.class, FProxyInterface.METHOD_NAME_SETMETHODINTERCEPTOR,
                FMethodInterceptor.class);

        FieldId fieldMethodInterceptor = helper.getField(helper.getProxyClass(),
                FMethodInterceptor.class, FProxyInterface.FIELD_NAME_METHODINTERCEPTOR);

        code.iput(fieldMethodInterceptor,
                helper.getThis(code),
                helper.getParameter(code, 0, FMethodInterceptor.class));
        code.returnVoid();

        /**
         * public FMethodInterceptor getMethodInterceptor$FProxy$()
         * {
         *     return this.mMethodInterceptor;
         * }
         */
        code = helper.declareMethod(Modifier.PUBLIC,
                FMethodInterceptor.class, FProxyInterface.METHOD_NAME_GETMETHODINTERCEPTOR);

        Local<FMethodInterceptor> localMethodInterceptor = helper.newLocal(code, FMethodInterceptor.class);
        code.iget(fieldMethodInterceptor, localMethodInterceptor, helper.getThis(code));
        code.returnValue(localMethodInterceptor);

        // ---------- FProxyInterface接口方法end ----------

        final Method[] arrMethod = helper.getSuperClass().getDeclaredMethods();

        String methodName = null;
        String methodNameSuper = null;
        Class<?> classReturn = null;
        boolean isReturnVoid = false;
        Class<?>[] classArgs = null;

        MethodId<?, ?> methodNotifyInterceptor = helper.getMethod(FProxyHelper.class,
                Object.class, FProxyHelper.METHOD_NAME_NOTIFYINTERCEPTOR,
                String.class, Class[].class, Object[].class, Object.class);

        int modifiers = 0;
        for (Method item : arrMethod)
        {
            modifiers = item.getModifiers();
            methodName = item.getName();
            if (methodName.contains("$") ||
                    modifiers == 0 ||
                    Modifier.isStatic(modifiers) ||
                    Modifier.isFinal(modifiers) ||
                    Modifier.isPrivate(modifiers))
            {
                continue;
            }
            classReturn = item.getReturnType();
            isReturnVoid = classReturn.getSimpleName().equals("void");
            classArgs = item.getParameterTypes();

            code = helper.declareMethod(item.getModifiers(), classReturn, methodName, classArgs); // 生成方法体

            // ---------- 变量 ----------

            // 保存返回值
            Local localReturn = helper.newLocal(code, classReturn);
            Local localReturnPack = null;
            if (classReturn.isPrimitive())
            {
                localReturnPack = helper.newLocal(code, DexMakerHelper.getPackedClass(classReturn));
            }

            // 保存方法拦截的返回值
            Local<Object> localReturnInterceptor = helper.newLocal(code, Object.class);

            Local<String> localMethodName = helper.newLocal(code, String.class);
            Local<Class[]> localArgsClass = helper.newLocal(code, Class[].class);
            Local<Object[]> localArgsValue = helper.newLocal(code, Object[].class);

            Local<Integer> localIntTmp = helper.newLocal(code, int.class);
            Local<Class> localClassTmp = helper.newLocal(code, Class.class);
            Local<Object> localObjectTmp = helper.newLocal(code, Object.class);

            // ---------- 变量赋值 ----------
            code.loadConstant(localMethodName, methodName);

            if (classArgs.length > 0)
            {
                code.loadConstant(localIntTmp, classArgs.length);
                code.newArray(localArgsClass, localIntTmp);
                code.newArray(localArgsValue, localIntTmp);

                Class<?> classArg = null;
                for (int i = 0; i < classArgs.length; i++)
                {
                    classArg = classArgs[i];

                    code.loadConstant(localIntTmp, i);
                    code.loadConstant(localClassTmp, classArg);
                    code.aput(localArgsClass, localIntTmp, localClassTmp);

                    if (classArg.isPrimitive())
                    {
                        MethodId methodValueOf = helper.getMethodPrimitiveValueOf(classArg);
                        code.invokeStatic(methodValueOf,
                                localObjectTmp,
                                helper.getParameter(code, i, classArg));

                        code.aput(localArgsValue, localIntTmp, localObjectTmp);
                    } else
                    {
                        code.aput(localArgsValue, localIntTmp, helper.getParameter(code, i, classArg));
                    }
                }
            } else
            {
                code.loadConstant(localArgsClass, null);
                code.loadConstant(localArgsValue, null);
            }

            // 调用拦截对象
            code.invokeStatic(methodNotifyInterceptor,
                    isReturnVoid ? null : localReturnInterceptor,
                    localMethodName, localArgsClass, localArgsValue, helper.getThis(code));

            if (isReturnVoid)
            {
                code.returnVoid();
            } else
            {
                if (classReturn.isPrimitive())
                {
                    Label ifNull = new Label();
                    code.loadConstant(localReturnPack, null);
                    code.compare(Comparison.EQ, ifNull, localReturnInterceptor, localReturnPack);

                    code.cast(localReturnPack, localReturnInterceptor);
                    MethodId methodPrimitiveValue = helper.getMethodPrimitiveValue(classReturn);
                    code.invokeVirtual(methodPrimitiveValue, localReturn, localReturnPack);
                    code.returnValue(localReturn);

                    code.mark(ifNull);
                    code.loadConstant(localReturn, 0);
                    code.returnValue(localReturn);
                } else
                {
                    code.cast(localReturn, localReturnInterceptor);
                    code.returnValue(localReturn);
                }
            }

            // 创建调用父类的方法
            methodNameSuper = methodName + FProxyInterface.PROXY_CLASS_INVOKE_SUPER_METHOD_SUFFIX;
            code = helper.declareMethod(Modifier.PUBLIC, classReturn, methodNameSuper, classArgs);

            localReturn = helper.newLocal(code, classReturn);
            Local[] localArgsValueSuper = null;

            MethodId methodSuper = helper.getMethod(helper.getSuperClass(), classReturn, methodName, classArgs);

            if (classArgs.length > 0)
            {
                localArgsValueSuper = new Local[classArgs.length];
                for (int i = 0; i < classArgs.length; i++)
                {
                    localArgsValueSuper[i] = helper.getParameter(code, i, classArgs[i]);
                }

                code.invokeSuper(methodSuper, isReturnVoid ? null : localReturn, helper.getThis(code),
                        localArgsValueSuper);
            } else
            {
                code.invokeSuper(methodSuper, isReturnVoid ? null : localReturn, helper.getThis(code));
            }

            if (isReturnVoid)
            {
                code.returnVoid();
            } else
            {
                code.returnValue(localReturn);
            }
        }
    }

    private static boolean deleteFileOrDir(File path)
    {
        if (path == null || !path.exists())
        {
            return true;
        }
        if (path.isFile())
        {
            return path.delete();
        }
        File[] files = path.listFiles();
        if (files != null)
        {
            for (File file : files)
            {
                deleteFileOrDir(file);
            }
        }
        return path.delete();
    }
}
