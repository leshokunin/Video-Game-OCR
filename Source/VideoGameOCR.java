import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.sourceforge.tess4j.Word;

import org.languagetool.*;
import org.languagetool.language.AmericanEnglish;
import org.languagetool.rules.*;

public class VideoGameOCR {
	private static boolean DEBUG_MODE = false;
	private static boolean USE_FAKE_OCR = false;
	private static boolean USE_STATIC_OCR = false;
	private static boolean USE_CUSTOM_DICTIONARY = false;
	private static boolean USE_BLACKLIST = false;
	private static boolean USE_SPELLCHECKER = false;
	private static boolean PRINT_WORDS = true;
	private static float CONFIDENCE = 79f;
	private static float RESOLUTION_DIVIDER = 1f;
	private static String CSV_HEADER = "ID, TimeStamp, Text, Confidence (%), Position";

	private static boolean isRunning = true;
	private static OCRManager ocrm;
	private static PrintWriter outWriter;
	private static ArrayList<String> prevStrings;
	private static JLanguageTool langTool;

	// Files
	private static String allowedWordsFile = "configs/allowedWords.txt";
	private static String bannedWordsFile = "configs/bannedWords.txt";
	private static String outputFile = "output.txt";

	private static ArrayList<String> allowedWords;
	private static ArrayList<String> bannedWords;
	private static long frameID = 1;
	private static String WHITELIST = "ABCDEFGHIJKLMNOPQRSTUVXYWZabcdefghijklmnopqrstuvwxyz0123456789";

	public static void main(String[] args) {
		HandleCommandArgs(args);
		boolean first_frame = true;
		try {
			if (DEBUG_MODE) {
				System.out.print("Initializing components...");
			}

			if (USE_CUSTOM_DICTIONARY) {
				LoadAllowedWords();
			}
			if (USE_BLACKLIST) {
				LoadBannedWords();
			}

			// Init output file
			if (USE_SPELLCHECKER) {
				langTool = new JLanguageTool(new AmericanEnglish());
				for (Rule rule : langTool.getAllRules()) {
					if (!rule.isDictionaryBasedSpellingRule()) {
						langTool.disableRule(rule.getId());
					}
				}
			}

			prevStrings = new ArrayList<String>();
			FileWriter fw = new FileWriter(outputFile, false);
			BufferedWriter bw = new BufferedWriter(fw);
			outWriter = new PrintWriter(bw);
			outWriter.println(CSV_HEADER);
			SetupShutdownHook();
			ocrm = new OCRManager(DEBUG_MODE);
			ocrm.Init(WHITELIST, RESOLUTION_DIVIDER);

			if (DEBUG_MODE) {
				System.out.println(" Done!");
			}

			ArrayList<Word> result = new ArrayList<Word>();
			while (isRunning) {
				if (DEBUG_MODE) {
					System.out.println("-----------------------");
				}
				ocrm.GetScreenshot();
				if (!USE_FAKE_OCR) {
					result = ocrm.GetOCR();
					if (first_frame) {
						first_frame = false;
						new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
						System.out.flush();
					}
				} else if (DEBUG_MODE) {
					result = ocrm.GetFakeOCR(USE_STATIC_OCR);
				}
				SaveResult(result);
				frameID++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void LoadAllowedWords() throws IOException {
		allowedWords = new ArrayList<String>();
		BufferedReader br = new BufferedReader(new FileReader(allowedWordsFile));
		try {
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();

			while (line != null) {
				sb.append(line);
				sb.append(System.lineSeparator());
				line = br.readLine();
				allowedWords.add(line);
			}
		} finally {
			br.close();
		}
	}

	private static void LoadBannedWords() throws IOException {
		bannedWords = new ArrayList<String>();
		BufferedReader br = new BufferedReader(new FileReader(bannedWordsFile));
		try {
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();

			while (line != null) {
				sb.append(line);
				sb.append(System.lineSeparator());
				line = br.readLine();
				bannedWords.add(line);
			}
		} finally {
			br.close();
		}
	}

	private static void SetupShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				if (DEBUG_MODE) {
					System.out.print("Exiting...");
				}
				outWriter.close();
				if (DEBUG_MODE) {
					System.out.println(" Done!");
				}
			}
		});
	}

	private static void SaveResult(ArrayList<Word> result) {
		if (result.size() > 0) {
			Date date = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy h:mm:ss a");
			String dateString = sdf.format(date);

			boolean newContent = false;
			ArrayList<String> newStrings = new ArrayList<String>();

			if (DEBUG_MODE) {
				System.out.println("Saving results to: " + outputFile);
			}

			for (Word w : result) {
				String s = w.getText();
				float c = w.getConfidence();

				// Check whether the string is not empty, if there was already new content or if
				// the current line is new and if the word is valid
				if (!s.isEmpty() && !prevStrings.contains(s) && !newStrings.contains(s) && IsValidWord(s)
						&& c > CONFIDENCE) {
					newContent = true;

					if (USE_SPELLCHECKER) {
						List<RuleMatch> matches;
						try {
							matches = langTool.check(s);
							if (matches.size() == 0) {
								PrintCSV(dateString, w);
							}
						} catch (IOException e) {
						}
					} else {
						PrintCSV(dateString, w);
					}
				}

				newStrings.add(s);
			}

			if (newContent) {
				outWriter.flush();
				prevStrings.clear();
				prevStrings = new ArrayList<String>(newStrings);
			}
		}
	}

	private static boolean IsValidWord(String w) {
		return (!USE_CUSTOM_DICTIONARY || allowedWords.contains(w)) && (!USE_BLACKLIST || !bannedWords.contains(w));
	}

	private static void PrintCSV(String dateString, Word w) {
		outWriter.println(frameID + ", " + dateString + ", " + w.getText() + ", " + w.getConfidence() + ", ("
				+ w.getBoundingBox().x + ";" + w.getBoundingBox().y + ")");

		if (DEBUG_MODE && PRINT_WORDS) {
			System.out.println(w.getText() + " => " + w.getConfidence() + "%");
		}
	}

	private static void HandleCommandArgs(String[] args) {
		int index = 0;
		for (String a : args) {
			switch (a) {
			case "-d":
				DEBUG_MODE = true;
				System.out.println("DEBUG MODE: ON");
				break;
			case "-sc":
				USE_SPELLCHECKER = true;
				System.out.println("USE SPELLCHECKER: ON");
				break;
			case "-c":
				CONFIDENCE = Float.parseFloat(args[index + 1]);
				System.out.println("CONFIDENCE: " + CONFIDENCE);
				break;
			case "-wl":
				WHITELIST = args[index + 1];
				System.out.println("WHITELIST: " + WHITELIST);
				break;
			case "-bl":
				USE_BLACKLIST = true;
				System.out.println("USE BLACKLIST : ON");
				break;
			case "-cd":
				USE_CUSTOM_DICTIONARY = true;
				System.out.println("USE CUSTOM DICTIONARY : ON");
				break;
			case "-r":
				RESOLUTION_DIVIDER = Float.parseFloat(args[index + 1]);
				System.out.println(
						"RESOLUTION: " + (int) (Toolkit.getDefaultToolkit().getScreenSize().height / RESOLUTION_DIVIDER)
								+ "x" + (int) (Toolkit.getDefaultToolkit().getScreenSize().width / RESOLUTION_DIVIDER));
				break;
			}
			index++;
		}
	}
}