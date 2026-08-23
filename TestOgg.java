import uk.creatopia.unbound.dead_air.music.OggDurationReader;
import java.io.FileInputStream;
public class TestOgg {
  public static void main(String[] args) throws Exception {
    try (FileInputStream in = new FileInputStream(args[0])) {
      System.out.println(OggDurationReader.readDurationSeconds(in));
    }
  }
}
