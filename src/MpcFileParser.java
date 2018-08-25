import SSLibrary.MultipartyOperation;
import SSLibrary.Operation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class MpcFileParser {

  public static ArrayList<MultipartyOperation> getMpcProgramForString(String input) {
    ArrayList<MultipartyOperation> mpcProgram = new ArrayList<>();

    String[] lines = input.split("\n");
    MultipartyOperation mpc;

    for (String line : lines) {
      mpc = getMpcFromLine(line);

      if (mpc != null) {
        mpcProgram.add(mpc);
      }
    }
    return mpcProgram;
  }

  private static MultipartyOperation getMpcFromLine(String line) {
    if (line.startsWith("//")) {
      return null;
    }

    line = line.replace(" ", "").toLowerCase();
    int indexOfEqual = line.indexOf("=");
    int indexOfSumOperator = line.indexOf("+");
    int indexOfEnd = line.indexOf(";");
    int indexOfMulOperator = line.indexOf("*");

    if (indexOfEqual <= 0 || indexOfEnd <= 0) {
      return null;
    }

    String outputKey = line.substring(0, indexOfEqual);
    String inputKey1;
    String inputKey2 = "";
    Operation operation = Operation.None;

    if (indexOfMulOperator > 0) {
      inputKey1 = line.substring(indexOfEqual + 1, indexOfMulOperator);
      inputKey2 = line.substring(indexOfMulOperator + 1, indexOfEnd);
      operation = Operation.Product;
    } else if (indexOfSumOperator > 0) {
      inputKey1 = line.substring(indexOfEqual + 1, indexOfSumOperator);
      inputKey2 = line.substring(indexOfSumOperator + 1, indexOfEnd);
      operation = Operation.Sum;
    } else {
      inputKey1 = line.substring(indexOfEqual + 1, indexOfEnd);
    }

    return new MultipartyOperation(outputKey, inputKey1, inputKey2, operation);
  }

  private static Map.Entry<String, BigInteger> getSecretFromLine(String line) {
    line = line.replace(" ", "").toLowerCase();
    int indexOfEqual = line.indexOf("=");
    int indexOfEnd = line.indexOf(";");

    if (indexOfEqual <= 0 || indexOfEnd <= 0) {
      return null;
    }

    String secretKey = line.substring(0, indexOfEqual);
    BigInteger secretValue =
        BigInteger.valueOf(Long.parseLong(line.substring(indexOfEqual + 1, indexOfEnd)));

    return new Map.Entry<String, BigInteger>() {
      @Override
      public String getKey() {
        return secretKey;
      }

      @Override
      public BigInteger getValue() {
        return secretValue;
      }

      @Override
      public BigInteger setValue(BigInteger value) {
        return null;
      }
    };
  }

  public static HashMap<String, BigInteger> getSecretsFromString(String input) {
    HashMap<String, BigInteger> secrets = new HashMap<>();
    String[] lines = input.split("\n");
    Map.Entry<String, BigInteger> secret;

    for (String line : lines) {
      secret = getSecretFromLine(line);

      if (secret != null) {
        secrets.put(secret.getKey(), secret.getValue());
      }
    }

    return secrets;
  }
}
