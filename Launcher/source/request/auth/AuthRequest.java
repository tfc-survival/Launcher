package launcher.request.auth;

import launcher.ConfigBin;
import launcher.HackHandler;
import launcher.LauncherAPI;
import launcher.client.ClientProfile;
import launcher.client.PlayerProfile;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.request.Request;
import launcher.request.auth.AuthRequest.Result;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.signed.SignedObjectHolder;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AuthRequest extends Request<Result> {
    private final String login;
    private final byte[] encryptedPassword;

    @LauncherAPI
    public AuthRequest(ConfigBin config, String login, byte[] encryptedPassword) {
        super(config);
        this.login = VerifyHelper.verify_1(login, VerifyHelper.NOT_EMPTY, "Login can't be empty");
        this.encryptedPassword = encryptedPassword.clone();
    }

    @LauncherAPI
    public AuthRequest(String login, byte[] encryptedPassword) {
        this(null, login, encryptedPassword);
    }

    public static byte[] hash1(String e) {
        try {
            MessageDigest md1 = MessageDigest.getInstance("SHA-512");
            md1.update(new byte[]{-10, 127, 90, 126, -78, 119, -13, -30, -101, 104, -94, 119, -66, 80, -17, 36});
            return md1.digest(e.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exc) {
            throw new RuntimeException(exc);
        }
    }

    @LauncherAPI
    public static byte[] getHWID() {
        SystemInfo systemInfo = new SystemInfo();
        CentralProcessor.ProcessorIdentifier processorIdentifier = systemInfo.getHardware().getProcessor().getProcessorIdentifier();
        ComputerSystem computerSystem = systemInfo.getHardware().getComputerSystem();

        String processorID = processorIdentifier.getProcessorID();
        String processorName = processorIdentifier.getName();
        String hardwareUUID = computerSystem.getHardwareUUID();
        String boardSerialNumber = computerSystem.getBaseboard().getSerialNumber();

        return
                hash1(processorID + "/" +
                        processorName + "/" +
                        hardwareUUID + "/" +
                        boardSerialNumber);
    }

    @Override
    public Type getType() {
        return Type.AUTH;
    }

    @Override
    protected Result requestDo(HInput input, HOutput output) throws Throwable {
        output.writeString(login, 255);
        output.writeByteArray(encryptedPassword, SecurityHelper.CRYPTO_MAX_LENGTH);
        output.writeByteArray(getHWID(), SecurityHelper.HWID_MAX_LENGTH);
        output.writeBoolean(HackHandler.isHacked());
        output.flush();

        // Read UUID and access token
        readError(input);
        PlayerProfile pp = new PlayerProfile(input);
        int jwt_length = input.readInt();
        String accessToken = input.readASCII(-jwt_length);

        // Read clients profiles list
        int count = input.readLength(0);
        List<SignedObjectHolder<ClientProfile>> profiles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            profiles.add(new SignedObjectHolder<>(input, config.publicKey, ClientProfile.RO_ADAPTER));
        }

        //SecurityHelper.verifyCertificates(Launcher.class);

        return new Result(pp, accessToken, profiles);
    }

    public static final class Result {
        @LauncherAPI
        public final PlayerProfile pp;
        @LauncherAPI
        public final String accessToken;
        @LauncherAPI
        public final List<SignedObjectHolder<ClientProfile>> profiles;

        public Result(PlayerProfile pp, String accessToken, List<SignedObjectHolder<ClientProfile>> profiles) {
            this.pp = pp;
            this.accessToken = accessToken;
            this.profiles = Collections.unmodifiableList(profiles);
        }
    }
}
