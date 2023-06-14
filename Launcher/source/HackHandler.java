package launcher;

public class HackHandler {
    public static boolean isHacked() {
        return launcher.helper.SecurityHelper.isValidCertificate();
    }
}
