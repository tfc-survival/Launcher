package launcher.request.auth;

import launcher.HackHandler;
import launcher.Launcher;
import launcher.Launcher.Config;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AuthRequest extends Request<Result> {
    private final String login;
    private final byte[] encryptedPassword;

    @LauncherAPI
    public AuthRequest(Config config, String login, byte[] encryptedPassword) {
        super(config);
        this.login = VerifyHelper.verify_1(login, VerifyHelper.NOT_EMPTY, "Login can't be empty");
        this.encryptedPassword = encryptedPassword.clone();
    }

    @LauncherAPI
    public AuthRequest(String login, byte[] encryptedPassword) {
        this(null, login, encryptedPassword);
    }

    @Override
    public Type getType() {
        return Type.AUTH;
    }

    @Override
    protected Result requestDo(HInput input, HOutput output) throws Throwable {
        output.writeString(login, 255);
        output.writeByteArray(encryptedPassword, SecurityHelper.CRYPTO_MAX_LENGTH);
        output.writeByteArray(Launcher.getHWID(), SecurityHelper.HWID_MAX_LENGTH);
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
