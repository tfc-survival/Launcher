package launcher.runtime.dialog.overlay.settings;

public class Account {
    public final String login;
    public final byte[] rsaPassword;

    public Account(String login, byte[] rsaPassword) {
        this.login = login;
        this.rsaPassword = rsaPassword;
    }
}
