package ua.vladaxon.serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Main {

    public static void main(String[] args) throws Exception {
        Map<String, String> testObject = new HashMap<>();
        testObject.put("a", "a");
        // stdSerialize(testObject);

        // Object testObject = new char[] { 'a', 'b' };
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        LSerializer ser = new LSerializer();
        ser.serialize(bout, testObject);
        byte[] serialized = bout.toByteArray();
        System.out.println(serialized.length);
        ByteArrayInputStream bin = new ByteArrayInputStream(serialized);
        Object deserialized = new LDesirializer().deserialize(bin, testObject.getClass());
        System.out.println(Objects.equals(testObject, deserialized));
    }

    private static void stdSerialize(Object obj) throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bout);
        out.writeObject(obj);
        ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
        ObjectInputStream in = new ObjectInputStream(bin);
        Object deserialized = in.readObject();
        System.out.println(obj.equals(deserialized));
    }

}
