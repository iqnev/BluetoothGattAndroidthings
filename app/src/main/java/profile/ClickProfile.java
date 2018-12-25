package profile;

import java.nio.charset.Charset;
import java.util.UUID;

public class ClickProfile {
    public static UUID DESCRIPTOR_CONFIG = UUID.fromString("78dbf521-3429-4fc2-877d-d3ac7c150e28");
    public static UUID DESCRIPTOR_USER_DESC = UUID.fromString("e41f8fdf-1a21-47b5-96f1-f4995f5ac8d1");

    public static UUID SERVICE_UUID = UUID.fromString("fd14c07c-2dbf-47d8-afb2-563bffab1e8c");
    public static UUID CHARACTERISTIC_COUNTER_UUID = UUID.fromString("7f6adfc7-192f-4057-8f4d-82c0cd6f0811");
    public static UUID CHARACTERISTIC_INTERACTOR_UUID = UUID.fromString("643f3fe8-33e3-4393-9032-1c9bb1687d9e");

    public static byte[] getUserDescription(UUID characteristicUUID) {
        String desc;

        if (CHARACTERISTIC_COUNTER_UUID.equals(characteristicUUID)) {
            desc = "Indicates the number of time you have been awesome so far";
        } else if (CHARACTERISTIC_INTERACTOR_UUID.equals(characteristicUUID)) {
            desc = "Awesomeness counter";
        } else {
            desc = "";
        }

        return desc.getBytes(Charset.forName("UTF-8"));
    }
}
