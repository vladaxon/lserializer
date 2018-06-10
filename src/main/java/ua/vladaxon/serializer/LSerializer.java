package ua.vladaxon.serializer;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Сериализатор объектов. Производит преобразования объекта в байтовое представление.
 *
 * @author Vladislav Babushkin
 *
 */
public class LSerializer {

    /** Счетчик идентификаторов объектов. */
    private long objIdentifier = 1;
    /** Куча сериализируемых объектов. */
    private final Map<Object, Long> heap = new IdentityHashMap<Object, Long>();
    /** Логгер. */
    private static final Logger LOG = LoggerFactory.getLogger(LSerializer.class);

    /**
     * Производит сериализацию объекта.
     *
     * @param out поток для записи данных
     * @param obj сериализуемый объект
     * @throws Exception при ошибке записи объекта
     */
    public void serialize(OutputStream out, Object obj) throws Exception {
        heap.clear();
        objIdentifier = 1;
        DataOutputStream dout = new DataOutputStream(out);
        Class<?> objClass = obj.getClass();
        if (objClass.isArray()) {
            writeArray(objClass.getComponentType(), obj, dout);
        } else if (objClass.isEnum()) {
            writeEnum(obj, dout);
        } else {
            writeObject(obj, dout);
        }
    }

    /**
     * Производит запись объекта в исходящий поток.
     *
     * @param obj записываемый объект
     * @param dout исходящий поток
     *
     * @throws Exception
     */
    private void writeObject(Object obj, DataOutputStream dout) throws Exception {
        Class<?> clazz = obj.getClass();
        writeClassFields(dout, clazz, obj);
    }

    /**
     * Производит запись полей объекта в исходящий поток.
     *
     * @param dout исходящий поток
     * @param clazz текущий класс для получения полей
     * @param obj объект, поля которого будут записаны
     * @throws Exception при ошибке записи данных
     */
    private void writeClassFields(DataOutputStream dout, Class<?> clazz, Object obj) throws Exception {
        // Заходим рекурсивно в родительские классы
        Class<?> superClazz = clazz.getSuperclass();
        if (superClazz != null && !superClazz.equals(Object.class)) {
            LOG.debug("Detect superclass {}", superClazz.getName());
            writeClassFields(dout, superClazz, obj);
        }
        // В конце добавляем свои классы
        Field[] fields = clazz.getDeclaredFields();
        if (fields.length > 0) {
            LOG.debug("Write fields of {} ...", clazz.getName());
        }
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            writeField(field, obj, dout);
        }
        LOG.debug("Class {} fields serialized", clazz.getName());
    }

    private void writeField(Field field, Object obj, DataOutputStream dout) throws Exception {
        field.setAccessible(true);
        if (Modifier.isStatic(field.getModifiers())) {
            // LOG.debug("Skip static field \"{}\"", field.getName());
        } else {
            Class<?> fieldType = field.getType();
            LOG.debug("Field \"{}\" of type \"{}\"", field.getName(), fieldType.getSimpleName());
            if (fieldType.isPrimitive()) {
                writePrimitive(field, obj, dout);
            } else {
                Object fieldObject = field.get(obj);
                if (fieldObject == null) {
                    dout.writeLong(0);
                } else if (heap.containsKey(fieldObject)) {
                    dout.writeLong(heap.get(fieldObject));
                } else {
                    Class<?> objectType = fieldObject.getClass();
                    long objID = nextObjectID();
                    dout.writeLong(objID);
                    heap.put(fieldObject, objID);
                    writeClassName(objectType, dout);
                    if (objectType.isArray()) {
                        writeArray(objectType.getComponentType(), fieldObject, dout);
                    } else if (objectType.isEnum()) {
                        writeEnum(fieldObject, dout);
                    } else {
                        writeObject(fieldObject, dout);
                    }
                }
            }
        }
    }

    private void writePrimitive(Field field, Object obj, DataOutputStream dout) throws Exception {
        if (field.getType().equals(byte.class)) {
            dout.writeByte(field.getByte(obj));
            return;
        }
        if (field.getType().equals(short.class)) {
            dout.writeShort(field.getShort(obj));
            return;
        }
        if (field.getType().equals(int.class)) {
            dout.writeInt(field.getInt(obj));
            return;
        }
        if (field.getType().equals(long.class)) {
            dout.writeLong(field.getLong(obj));
            return;
        }
        if (field.getType().equals(float.class)) {
            dout.writeFloat(field.getFloat(obj));
            return;
        }
        if (field.getType().equals(double.class)) {
            dout.writeDouble(field.getDouble(obj));
            return;
        }
        if (field.getType().equals(boolean.class)) {
            dout.writeBoolean(field.getBoolean(obj));
            return;
        }
        if (field.getType().equals(char.class)) {
            dout.writeChar(field.getChar(obj));
            return;
        }
    }

    /**
     * Записывает массив элементов в исходящий поток.
     *
     * @param arrType тип элементов массива
     * @param array массив элементов
     * @param dout исходящий поток
     * @throws Exception при ошибке записи данных
     */
    private void writeArray(Class<?> arrType, Object array, DataOutputStream dout) throws Exception {
        int length = Array.getLength(array);
        LOG.debug("Write array of {}, length {}", arrType.getName(), length);
        dout.writeInt(length);
        for (int i = 0; i < length; i++) {
            writeArrayElement(dout, array, i);
        }
        LOG.debug("Written {}", dout.size());
    }

    /**
     * Записывает элемент массива в исходящий поток.
     *
     * @param dout исходящий поток для записи данных
     * @param array массив данных
     * @param index индекс элемента, который будет записан
     * @throws Exception при ошибке записи элемента
     */
    private void writeArrayElement(DataOutputStream dout, Object array, int index) throws Exception {
        Class<?> arrType = array.getClass().getComponentType();
        if (arrType.isPrimitive()) {
            if (arrType.equals(byte.class)) {
                dout.writeByte(Array.getByte(array, index));
                return;
            }
            if (arrType.equals(short.class)) {
                dout.writeShort(Array.getShort(array, index));
                return;
            }
            if (arrType.equals(int.class)) {
                dout.writeInt(Array.getInt(array, index));
                return;
            }
            if (arrType.equals(long.class)) {
                dout.writeLong(Array.getLong(array, index));
                return;
            }
            if (arrType.equals(float.class)) {
                dout.writeFloat(Array.getFloat(array, index));
                return;
            }
            if (arrType.equals(double.class)) {
                dout.writeDouble(Array.getDouble(array, index));
                return;
            }
            if (arrType.equals(boolean.class)) {
                dout.writeBoolean(Array.getBoolean(array, index));
                return;
            }
            if (arrType.equals(char.class)) {
                dout.writeChar(Array.getChar(array, index));
                return;
            }
        } else {
            Object objElement = Array.get(array, index);
            LOG.debug("Object element {}", objElement);
            if (objElement == null) {
                dout.writeLong(0);
            } else if (heap.containsKey(objElement)) {
                dout.writeLong(heap.get(objElement));
            } else {
                long objID = nextObjectID();
                LOG.debug("Object ID {}", objID);
                dout.writeLong(objID);
                heap.put(objElement, objID);
                Class<?> elemType = objElement.getClass();
                writeClassName(elemType, dout);
                if (elemType.isArray()) {
                    writeArray(arrType, objElement, dout);
                } else if (elemType.isEnum()) {
                    writeEnum(objElement, dout);
                } else {
                    writeObject(objElement, dout);
                }
            }
        }
    }

    /**
     * Записывает элемент перечисления в исходящий поток.
     *
     * @param obj записываемый элемент
     * @param dout поток для записи данных
     * @throws Exception при ошибке записи
     */
    private void writeEnum(Object obj, DataOutputStream dout) throws Exception {
        // Записываем перечисления имя как массив чаров
        writeArray(char.class, obj.toString().toCharArray(), dout);
    }

    /**
     * Возвращает следующий идентификатор объекта.
     *
     * @return идентификатор объекта
     */
    private long nextObjectID() {
        return objIdentifier++;
    }

    /**
     * Записывает имя класса в исходящий поток.
     *
     * @param clazz класс для записи
     * @param dout исходящий поток
     * @throws Exception при ошибке записи данных
     */
    private void writeClassName(Class<?> clazz, DataOutputStream dout) throws Exception {
        LOG.debug("Write class name {}", clazz.getName());
        char[] nameChars = clazz.getName().toCharArray();
        writeArray(char.class, nameChars, dout);
    }

}
