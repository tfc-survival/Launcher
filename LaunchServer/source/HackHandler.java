package launchserver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum HackHandler {
    instance;

    private Set<String> violatedClientsNicks = new HashSet<>();

    File save = new File("./violatedClients.txt");

    {
        if (save.exists()) {
            try (Stream<String> lines = Files.lines(save.toPath())) {
                violatedClientsNicks = lines.collect(Collectors.toSet());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void addNick(String nick) {
        if (!violatedClientsNicks.contains(nick)) {
            violatedClientsNicks.add(nick);
            try {
                Files.write(save.toPath(), (nick + "\n").getBytes(), save.exists() ? StandardOpenOption.APPEND : StandardOpenOption.WRITE);
            } catch (IOException e) {
                //exception handling left as an exercise for the reader
            }
        }
    }
}
