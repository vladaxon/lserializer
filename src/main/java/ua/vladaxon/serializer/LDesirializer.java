package ua.vladaxon.serializer;

import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import sun.reflect.ReflectionFactory;

/**
 * Десериализатор объектов. Производит создание объекта из входящего потока.
 *
 * @author Vladislav Babushkin
 *
 */
public class LDesirializer {

    /** Куча десериализируемых объектов. */
    private final Map<Long, Object> heap = new HashMap<Long, Object>();
    private final Map<Integer, Class<?>> classHeap = new HashMap<>();

    /**
     * Производит десериализацию объекта из потока.
     *
     * @param in входящий поток для получения данных
     * @param rootClass класс требуемого объекта
     * @return десериализованный объект
     * @throws Exception при ошибке считывания
     */
    public Object deserialize(InputStream in, Class<?> rootClass) throws Exception {
        DataInputStream din = new DataInputStream(in);
        if (rootClass.isArray()) {
            return readArray(din, rootClass.getComponentType());
        } else if (rootClass.isEnum()) {
            return readEnum(din, rootClass);
        } else {
            return readObject(din, rootClass);
        }
    }

    /**
     * Производит считывание объекта.
     *
     * @param din входящий поток
     * @param objClass класс объекта
     * @return считанный и инициализированный объект
     * @throws Exception при ошибке считывания объекта
     */
    private Object readObject(DataInputStream din, Class<?> objClass) throws Exception {
        ReflectionFactory rf = ReflectionFactory.getReflectionFactory();
        Constructor<?> objDef = objClass.getSuperclass().getDeclaredConstructor();
        Constructor<?> intConstr = rf.newConstructorForSerialization(objClass, objDef);
        Object objInstance = intConstr.newInstance();
        readClassFields(din, objClass, objInstance);
        return objInstance;
    }

    /**
     * Производит считывание полей класса.
     *
     * @param din входящий поток
     * @param objClass класс, для которого производится считывание полей
     * @param obj объект для установки значений
     * @throws Exception при ошибке считывания данных
     */
    private void readClassFields(DataInputStream din, Class<?> objClass, Object obj) throws Exception {
        Class<?> superClazz = objClass.getSuperclass();
        if (superClazz != null && !superClazz.equals(Object.class)) {
            readClassFields(din, superClazz, obj);
        }
        Field[] fields = objClass.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            readField(din, field, obj);
        }
    }

    /**
     * Производит считывание поля объекта.
     *
     * @param din входящий поток
     * @param field считываемое поле
     * @param parent родительский объект, содержащий поле
     * @throws Exception при ошибке считывания
     */
    private void readField(DataInputStream din, Field field, Object parent) throws Exception {
        field.setAccessible(true);
        if (!Modifier.isStatic(field.getModifiers())) {
            Class<?> fieldType = field.getType();
            if (fieldType.isPrimitive()) {
                readPrimitive(din, field, parent);
            } else {
                long objID = din.readLong();
                if (objID > 0) {
                    if (heap.containsKey(objID)) {
                        field.set(parent, heap.get(objID));
                    } else {
                        Class<?> objType = readClass(din);
                        if (objType.isArray()) {
                            Object array = readArray(din, objType.getComponentType());
                            heap.put(objID, array);
                            field.set(parent, array);
                        } else if (objType.isEnum()) {
                            Object enumObj = readEnum(din, objType);
                            heap.put(objID, enumObj);
                            field.set(parent, enumObj);
                        } else {
                            Object child = readObject(din, objType);
                            heap.put(objID, child);
                            field.set(parent, child);
                        }
                    }
                }
            }
        }
    }

    /**
     * Производит считывание примитивного типа и устанавливает его в поле.
     *
     * @param din входящий поток
     * @param field поле для установки значения
     * @param obj объект для установки значения
     * @throws Exception при ошибке считывания
     */
    private void readPrimitive(DataInputStream din, Field field, Object obj) throws Exception {
        Class<?> fieldType = field.getType();
        if (fieldType.equals(byte.class)) {
            field.set(obj, din.readByte());
            return;
        }
        if (fieldType.equals(short.class)) {
            field.set(obj, din.readShort());
            return;
        }
        if (fieldType.equals(int.class)) {
            field.set(obj, din.readInt());
            return;
        }
        if (fieldType.equals(long.class)) {
            field.set(obj, din.readLong());
            return;
        }
        if (fieldType.equals(float.class)) {
            field.set(obj, din.readFloat());
            return;
        }
        if (fieldType.equals(double.class)) {
            field.set(obj, din.readDouble());
            return;
        }
        if (fieldType.equals(boolean.class)) {
            field.set(obj, din.readBoolean());
            return;
        }
        if (fieldType.equals(char.class)) {
            field.set(obj, din.readChar());
            return;
        }
    }

    /**
     * Производит считывание массива из потока.
     *
     * @param din входящий поток
     * @param compType тип элементов массива
     * @return считанный массив
     * @throws Exception при ошибке считывания
     */
    private Object readArray(DataInputStream din, Class<?> compType) throws Exception {
        int length = din.readInt();
        Object array = Array.newInstance(compType, length);
        for (int i = 0; i < length; i++) {
            readArrayElement(din, array, i, compType);
        }
        return array;
    }

    /**
     * Производит считывание элемента массива.
     *
     * @param din входящий поток
     * @param array массив для установки считанного элемента
     * @param index индекс элемента
     * @param compType тип элемента
     * @throws Exception при ошибке считывания
     */
    private void readArrayElement(DataInputStream din, Object array, int index, Class<?> compType) throws Exception {
        if (compType.isPrimitive()) {
            if (compType.equals(byte.class)) {
                Array.setByte(array, index, din.readByte());
                return;
            }
            if (compType.equals(short.class)) {
                Array.setShort(array, index, din.readShort());
                return;
            }
            if (compType.equals(int.class)) {
                Array.setInt(array, index, din.readInt());
                return;
            }
            if (compType.equals(long.class)) {
                Array.setLong(array, index, din.readLong());
                return;
            }
            if (compType.equals(float.class)) {
                Array.setFloat(array, index, din.readFloat());
                return;
            }
            if (compType.equals(double.class)) {
                Array.setDouble(array, index, din.readDouble());
                return;
            }
            if (compType.equals(boolean.class)) {
                Array.setBoolean(array, index, din.readBoolean());
                return;
            }
            if (compType.equals(char.class)) {
                Array.setChar(array, index, din.readChar());
                return;
            }
        } else {
            long objID = din.readLong();
            if (objID > 0) {
                if (heap.containsKey(objID)) {
                    Array.set(array, index, heap.get(objID));
                } else {
                    Class<?> elementType = readClass(din);
                    if (elementType.isArray()) {
                        Object objArray = readArray(din, compType);
                        heap.put(objID, objArray);
                        Array.set(array, index, objArray);
                    } else if (elementType.isEnum()) {
                        Object enumObj = readEnum(din, compType);
                        heap.put(objID, enumObj);
                        Array.set(array, index, enumObj);
                    } else {
                        Object child = readObject(din, elementType);
                        heap.put(objID, child);
                        Array.set(array, index, child);
                    }
                }
            }
        }
    }

    /**
     * Производит считывание экземпляра перечисления.
     *
     * @param din входящий поток
     * @param enumClass класс перечисления
     * @return считанный объект перечисления
     * @throws Exception при ошибке считывания
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object readEnum(DataInputStream din, Class<?> enumClass) throws Exception {
        char[] enumName = (char[]) readArray(din, char.class);
        if (enumName != null) {
            return Enum.valueOf((Class<Enum>) enumClass, new String(enumName));
        } else {
            return null;
        }
    }

    /**
     * Производит считывание класса из потока.
     *
     * @param din входящий поток
     * @return класс объекта
     * @throws Exception при ошибке считывания класса
     */
    private Class<?> readClass(DataInputStream din) throws Exception {
        int classID = din.readInt();
        if (classHeap.containsKey(classID)) {
            return classHeap.get(classID);
        } else {
            char[] nameChars = (char[]) readArray(din, char.class);
            Class<?> newClass = Class.forName(new String(nameChars));
            classHeap.put(classID, newClass);
            return newClass;
        }
    }

}
