package launchserver.binary;

import launcher.Config;
import launcher.Launcher;
import launcher.LauncherAPI;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.SecurityHelper.DigestAlgorithm;
import launcher.serialize.HOutput;
import launchserver.LaunchServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.*;

public final class JARLauncherBinary extends LauncherBinary {
    @LauncherAPI
    public final Path runtimeDir;

    @LauncherAPI
    public JARLauncherBinary(LaunchServer server) throws IOException {
        super(server, server.dir.resolve(server.config.binaryName + ".jar"));
        runtimeDir = server.dir.resolve(Launcher.RUNTIME_DIR);
        tryUnpackRuntime();
    }

    private static ZipEntry newEntry(String fileName) {
        return IOHelper.newZipEntry(Launcher.RUNTIME_DIR + IOHelper.CROSS_SEPARATOR + fileName);
    }

    @Override
    public void build() throws IOException {
        tryUnpackRuntime();

        // Build launcher binary
        LogHelper.info("Building launcher binary file");
        try (JarOutputStream output = new JarOutputStream(IOHelper.newOutput(binaryFile))) {
            output.setMethod(ZipOutputStream.DEFLATED);
            output.setLevel(Deflater.BEST_COMPRESSION);
            try (InputStream input = new GZIPInputStream(IOHelper.newInput(IOHelper.getResourceURL("Launcher.pack.gz")))) {
                Pack200.newUnpacker().unpack(input, output);
            }

            // Write launcher runtime dir
            Map<String, byte[]> runtime = new HashMap<>(256);
            IOHelper.walk(runtimeDir, new RuntimeDirVisitor(output, runtime), false);

            // Create launcher config file
            byte[] launcherConfigBytes;
            try (ByteArrayOutputStream configArray = IOHelper.newByteArrayOutput()) {
                try (HOutput configOutput = new HOutput(configArray)) {
                    new Config(server.config.getAddress(), server.config.port, server.publicKey, runtime).write(configOutput);
                }
                launcherConfigBytes = configArray.toByteArray();
            }

            // Write launcher config file
            try {
                output.putNextEntry(IOHelper.newZipEntry(Config.CONFIG_FILE));
                output.write(launcherConfigBytes);
            } catch (ZipException e) {
                if (e.getMessage().contains("duplicate entry"))
                    LogHelper.warning(e.getMessage());
                else
                    throw e;
            }
        }
    }

    @LauncherAPI
    public void tryUnpackRuntime() throws IOException {
        // Verify is runtime dir unpacked
        if (IOHelper.isDir(runtimeDir)) {
            return; // Already unpacked
        }

        // Unpack launcher runtime files
        Files.createDirectory(runtimeDir);
        LogHelper.info("Unpacking launcher runtime files");
        try (ZipInputStream input = IOHelper.newZipInput(IOHelper.getResourceURL("launchserver/defaults/runtime.zip"))) {
            for (ZipEntry entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                if (entry.isDirectory()) {
                    continue; // Skip dirs
                }

                // Unpack runtime file
                IOHelper.transfer(input, runtimeDir.resolve(IOHelper.toPath(entry.getName())));
            }
        }
    }

    private final class RuntimeDirVisitor extends SimpleFileVisitor<Path> {
        private final ZipOutputStream output;
        private final Map<String, byte[]> runtime;

        private RuntimeDirVisitor(ZipOutputStream output, Map<String, byte[]> runtime) {
            this.output = output;
            this.runtime = runtime;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            String dirName = IOHelper.toString(runtimeDir.relativize(dir));
            try {
                output.putNextEntry(newEntry(dirName + '/'));
            } catch (ZipException e) {
                if (e.getMessage().contains("duplicate entry"))
                    LogHelper.warning(e.getMessage());
                else
                    throw e;

            }
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String fileName = IOHelper.toString(runtimeDir.relativize(file));
            runtime.put(fileName, SecurityHelper.digest(DigestAlgorithm.MD5, file));

            try {
                // Create zip entry and transfer contents
                output.putNextEntry(newEntry(fileName));
                IOHelper.transfer(file, output);
            } catch (ZipException e) {
                if (e.getMessage().contains("duplicate entry"))
                    LogHelper.warning(e.getMessage());
                else
                    throw e;

            }

            // Return result
            return super.visitFile(file, attrs);
        }
    }
}
