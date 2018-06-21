import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Random;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;

public class OCRManager {

	private ITesseract instance;
	private BufferedImage screenshot;
	private boolean DEBUG_MODE;
	private float divider = 1f;

	public OCRManager(boolean DEBUG_MODE) {
		this.DEBUG_MODE = DEBUG_MODE;
	}

	protected void Init(String whitelist, float divider) {
		instance = new Tesseract(); // JNA Interface Mapping
		instance.setDatapath("./configs");
		instance.setLanguage("eng");
		this.divider = divider;

		instance.setPageSegMode(ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT_OSD);
		instance.setTessVariable("tessedit_char_whitelist", whitelist);

		// TEST
		instance.setTessVariable("debug_file", "/dev/null");
		instance.setTessVariable("image_default_resolution", "1200");

		screenshot = new BufferedImage(1000, 1000, BufferedImage.TYPE_3BYTE_BGR);
	}

	protected void GetScreenshot() {
		if (DEBUG_MODE) {
			System.out.print("Getting screenshot!");
		}

		Rectangle screenRect = new Rectangle((int) (Toolkit.getDefaultToolkit().getScreenSize().height / divider),
				(int) (Toolkit.getDefaultToolkit().getScreenSize().width / divider));
		try {
			screenshot = new Robot().createScreenCapture(screenRect);
		} catch (AWTException e) {
			screenshot = null;
		}

		if (DEBUG_MODE) {
			System.out.println(" Done!");
		}
	}

	protected ArrayList<Word> GetOCR() {
		if (DEBUG_MODE) {
			System.out.print("Getting OCR...");
		}

		if (screenshot != null) {
			ArrayList<Word> r = DoOCR();

			if (DEBUG_MODE) {
				System.out.println(" Done!");
			}
			return r;
		}

		if (DEBUG_MODE) {
			System.out.println(" Failed!");
		}
		return null;
	}

	protected ArrayList<Word> GetFakeOCR(boolean isStatic) {
		ArrayList<Word> words = new ArrayList<Word>();

		if (!isStatic) {
			words.add(new Word(new Random().nextFloat() + "", new Random().nextFloat(), new Rectangle()));
		}
		words.add(new Word("test", new Random().nextFloat(), new Rectangle()));
		words.add(new Word("test1", new Random().nextFloat(), new Rectangle()));
		words.add(new Word("test2", new Random().nextFloat(), new Rectangle()));
		words.add(new Word("test3", new Random().nextFloat(), new Rectangle()));

		return words;
	}

	protected ArrayList<Word> DoOCR() {
		if (screenshot != null) {
			return (ArrayList<Word>) instance.getWords(screenshot, 3);
		} else {
			return new ArrayList<Word>();
		}
	}
}
