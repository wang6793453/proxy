package com.fanwe.lib.proxy;

import com.android.dx.Code;
import com.android.dx.DexMaker;
import com.android.dx.FieldId;
import com.android.dx.Local;
import com.android.dx.MethodId;
import com.android.dx.TypeId;

import java.util.HashMap;

/**
 * Created by Administrator on 2017/12/1.
 */
public class DexMakerHelper
{
    private HashMap<Class, TypeId> mMapType = new HashMap<>();

    private DexMaker mDexMaker;
    private Class<?> mSuperClass;

    private final TypeId<?> mTypeSuper;
    private final TypeId<?> mTypeSub;

    public DexMakerHelper(Class<?> superClass)
    {
        mSuperClass = superClass;

        mTypeSuper = getType(mSuperClass);
        final String typeSubName = mTypeSuper.getName().replace(";", FProxyInterface.PROXY_CLASS_SUFFIX + ";");
        mTypeSub = TypeId.get(typeSubName);
    }

    public String getProxyClassName()
    {
        return mSuperClass.getName() + FProxyInterface.PROXY_CLASS_SUFFIX;
    }

    public TypeId<?> getTypeSuper()
    {
        return mTypeSuper;
    }

    public TypeId<?> getTypeSub()
    {
        return mTypeSub;
    }

    public DexMaker getDexMaker()
    {
        if (mDexMaker == null)
        {
            mDexMaker = new DexMaker();
        }
        return mDexMaker;
    }

    /**
     * 声明类
     *
     * @param flags      权限
     * @param interfaces 要实现的接口
     */
    public void declareClass(int flags, Class<?>... interfaces)
    {
        TypeId<?>[] arrType = classToTypeId(interfaces);
        if (arrType != null)
        {
            getDexMaker().declare(getTypeSub(),
                    getTypeSub().getName(),
                    flags,
                    getTypeSuper(),
                    arrType);
        } else
        {
            getDexMaker().declare(getTypeSub(),
                    getTypeSub().getName(),
                    flags,
                    getTypeSuper());
        }
    }

    /**
     * 声明构造方法
     *
     * @param flags      权限
     * @param parameters 参数
     * @return
     */
    public Code declareConstructor(int flags, Class<?>... parameters)
    {
        MethodId method = getConstructor(FSub.class, parameters);
        return getDexMaker().declare(method, flags);
    }

    /**
     * 声明属性
     *
     * @param flags      权限
     * @param fieldClass 属性类型
     * @param fieldName  属性名称
     * @param fieldValue 属性值
     */
    public void declareField(int flags, Class<?> fieldClass, String fieldName, Object fieldValue)
    {
        FieldId<?, ?> field = getField(FSub.class, fieldClass, fieldName);
        getDexMaker().declare(field, flags, fieldValue);
    }

    /**
     * 声明方法
     *
     * @param flags       权限
     * @param classReturn 返回值类型
     * @param methodName  方法名称
     * @param parameters  方法类型class数组
     * @return
     */
    public Code declareMethod(int flags, Class<?> classReturn, String methodName, Class<?>... parameters)
    {
        MethodId<?, ?> method = getMethod(getTypeSub(), classReturn, methodName, parameters);
        return getDexMaker().declare(method, flags);
    }

    public <T> TypeId<T> getType(Class<T> clazz)
    {
        TypeId typeId = mMapType.get(clazz);
        if (typeId == null)
        {
            if (clazz == FSub.class)
            {
                typeId = getTypeSub();
            } else if (clazz == Void.class)
            {
                typeId = TypeId.VOID;
            } else if (clazz == Object.class)
            {
                typeId = TypeId.OBJECT;
            } else if (clazz == String.class)
            {
                typeId = TypeId.STRING;
            } else
            {
                typeId = TypeId.get(clazz);
            }

            mMapType.put(clazz, typeId);
        }
        return typeId;
    }

    /**
     * 获得属性
     *
     * @param classTarget 目标类
     * @param classField  属性类
     * @param fieldName   属性名称
     * @param <T>         目标类型
     * @param <F>         属性类型
     * @return
     */
    public <T, F> FieldId<T, F> getField(Class<T> classTarget, Class<F> classField, String fieldName)
    {
        TypeId typeTarget = getType(classTarget);
        TypeId typeField = getType(classField);

        FieldId<T, F> field = typeTarget.getField(typeField, fieldName);
        return field;
    }

    /**
     * 获得构造方法
     *
     * @param classTarget
     * @param parameters
     * @param <T>
     * @return
     */
    public <T> MethodId<T, Void> getConstructor(Class<T> classTarget, Class<?>... parameters)
    {
        TypeId<T> typeTarget = getType(classTarget);
        TypeId[] typeParameters = classToTypeId(parameters);

        if (typeParameters != null)
        {
            return typeTarget.getConstructor(typeParameters);
        } else
        {
            return typeTarget.getConstructor();
        }
    }

    public <T, R> MethodId<T, R> getMethod(TypeId<T> typeTarget, Class<R> classReturn, String methodName, Class<?>... parameters)
    {
        TypeId typeReturn = getType(classReturn);
        TypeId[] typeParameters = classToTypeId(parameters);

        if (typeParameters != null)
        {
            return typeTarget.getMethod(typeReturn, methodName, typeParameters);
        } else
        {
            return typeTarget.getMethod(typeReturn, methodName);
        }
    }

    public Local getThis(Code code)
    {
        return code.getThis(getTypeSub());
    }

    public <T> Local<T> getParameter(Code code, int index, Class<T> clazz)
    {
        TypeId<T> type = getType(clazz);
        return code.getParameter(index, type);
    }

    public TypeId<?>[] classToTypeId(Class<?>[] arrClass)
    {
        if (arrClass == null || arrClass.length <= 0)
        {
            return null;
        }

        TypeId<?>[] arrResult = new TypeId<?>[arrClass.length];
        for (int i = 0; i < arrClass.length; i++)
        {
            arrResult[i] = getType(arrClass[i]);
        }
        return arrResult;
    }

    public static Class<?> getPackedClass(Class<?> primitive)
    {
        if (primitive == boolean.class)
        {
            return Boolean.class;
        } else if (primitive == byte.class)
        {
            return Byte.class;
        } else if (primitive == char.class)
        {
            return Character.class;
        } else if (primitive == double.class)
        {
            return Double.class;
        } else if (primitive == float.class)
        {
            return Float.class;
        } else if (primitive == int.class)
        {
            return Integer.class;
        } else if (primitive == long.class)
        {
            return Long.class;
        } else if (primitive == short.class)
        {
            return Short.class;
        } else
        {
            return primitive;
        }
    }
}
