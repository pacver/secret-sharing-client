import SSLibrary.MultipartyOperation;
import SessionManagement.AdversaryManager;
import SessionManagement.DealingManager;
import SessionManagement.ParticipatingManager;
import SessionManagement.SessionRestartListener;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.stream.Stream;

public class SSClientMain implements SessionRestartListener {

  private ParticipatingManager _participatingManager;
  private DealingManager       _dealingManager;

  public static void main(String[] args) throws Exception {

    new SSClientMain().runClient();
  }

  private void runClient() throws Exception {

    System.out.println("SSC-QUESTION: Please choose your role for the next session.");
    System.out.println("              - Enter 'A' to act as an adversary.");
    System.out.println(
        "              - Enter 'L' to listen for new sessions and join them as player or combiner.");
    System.out.println("              - Enter 'D' to act as a dealer and to share a secret.");

    Scanner _scanner = new Scanner(System.in);

    String role = _scanner.nextLine().toUpperCase();

    switch (role) {
      case "A":
        AdversaryManager _adversarymanager = new AdversaryManager();
        _adversarymanager.setSessionRestartListener(this);
        System.out.println("SSC-INFO: Waiting for broadcast messages...");
        break;

      case "D":
        System.out.println("SSC-QUESTION: Enter a key for the secret to share e.g. pwd for safe");
        String inputKey = _scanner.nextLine();

        System.out.println(
            "SSC-QUESTION: Enter name of your mpc program file (default = ComplexSum.mpc). You can use an absolute path or a path relative to the 'InputFiles' folder.");
        String fileName = _scanner.nextLine();
        String mpcInputFile =
            fileName.equals("") ? GetFilePath("ComplexSum.mpc") : GetFilePath(fileName);

        System.out.println(
            "SSC-QUESTION: Enter name of your secret definition file (default = SampleInput.secrets). You can use an absolute path or a path relative to the 'InputFiles' folder.");
        fileName = _scanner.nextLine();
        String secretsInputFile =
            fileName.equals("") ? GetFilePath("SampleInput.secrets") : GetFilePath(fileName);

        System.out.println("SSC-INFO: Creating communication channel...");
        _dealingManager = new DealingManager();
        _dealingManager.setSessionRestartListener(this);

        String inputMpcProgram = readFileFromPath(mpcInputFile);
        String secretsInput = readFileFromPath(secretsInputFile);
        ArrayList<MultipartyOperation> mpcProgram =
            MpcFileParser.getMpcProgramForString(inputMpcProgram);
        HashMap<String, BigInteger> secretDefinitions =
            MpcFileParser.getSecretsFromString(secretsInput);

        System.out.println("SSC-INFO: Waiting for participants joining this session...");
        _dealingManager.triggerSessionStart(mpcProgram, secretDefinitions, inputKey);
        System.out.println();
        break;

      case "L":
        System.out.println("SSC-INFO: Creating communication channel...");
        _participatingManager = new ParticipatingManager();
        _participatingManager.setSessionRestartListener(this);
        break;

      default:
        onSessionCancelled();
    }
  }

  private String GetFilePath(String enteredFilePath) throws Exception {
    String finalPath = "";
    if (Paths.get(enteredFilePath).isAbsolute()) {
      finalPath = enteredFilePath;
    } else {
      File file = new File("");

      finalPath =
          Paths.get(file.getAbsolutePath().toString(), "InputFiles", enteredFilePath).toString();
    }

    if (Files.notExists(Paths.get(finalPath))) {
      System.out.println(String.format(
          "The input file %s does not exist. Session will be cancelled.",
          finalPath));
      onSessionCancelled();
      return "";
    }

    return finalPath;
  }

  private static String readFileFromPath(String filePath) {
    StringBuilder contentBuilder = new StringBuilder();

    try (Stream<String> stream = Files.lines(Paths.get(filePath), StandardCharsets.UTF_8)) {
      stream.forEach(s -> contentBuilder.append(s).append("\n"));
    } catch (IOException e) {
      e.printStackTrace();
    }

    return contentBuilder.toString();
  }

  @Override
  public void onSessionCancelled() {
    System.out.println();
    System.out.println();
    _dealingManager = null;
    _participatingManager = null;
  }
}
