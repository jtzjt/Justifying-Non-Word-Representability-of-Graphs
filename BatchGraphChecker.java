import graph.AdjacencyMatrix;
import graph.Graph;
import graph.SemiTransitiveReason;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.ArrayList;
import java.util.List;

/***BEGIN CLASS BatchGraphChecker.java******************************************
 * A utility class for batch checking word-representability of graphs from a
 * zip file. Each graph is stored as a .txt file containing only 0s and 1s,
 * where each line represents a row of the adjacency matrix.
 * 
 * Usage: java BatchGraphChecker <zip_file_path> [output_file_path]
 * 
 * @author julia
 *****************/public class BatchGraphChecker {/***************************/


//METHODS------------------------------/
public static void main(String[] args)
{	if (args.length < 1) {
		System.out.println("Usage: java BatchGraphChecker <zip_file_path> [output_file_path]");
		System.out.println("  zip_file_path: path to the zip file containing graph .txt files");
		System.out.println("  output_file_path: (optional) path to output results file");
		return;
	}

	String zipFilePath = args[0];
	String outputFilePath = (args.length > 1) ? args[1] : null;

	try {
		processZipFile(zipFilePath, outputFilePath);
	} catch (Exception e) {
		System.err.println("Error processing zip file: " + e.getMessage());
		e.printStackTrace();
	}
}


/* Process all graph files in a zip file
 */
private static void processZipFile(String zipFilePath, String outputFilePath) throws Exception
{	PrintWriter output = null;
	if (outputFilePath != null) {
		output = new PrintWriter(new FileWriter(outputFilePath));
	}

	List<Result> results = new ArrayList<Result>();

	try {
		FileInputStream fis = new FileInputStream(zipFilePath);
		ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));
		ZipEntry entry;

		System.out.println("Processing zip file: " + zipFilePath);
		int fileCount = 0;

		while ((entry = zis.getNextEntry()) != null) {
			String entryName = entry.getName();
			
			// Skip macOS system files and directories
			if (entryName.contains("__MACOSX") || 
			    entryName.contains("/._") || 
			    entryName.startsWith("._") ||
			    entry.isDirectory()) {
				zis.closeEntry();
				continue;
			}
			
			// Only process .txt files
			if (entryName.toLowerCase().endsWith(".txt") && !entry.isDirectory()) {
				fileCount++;
				System.out.println("Processing: " + entryName);
				
				Result result = processGraphFile(zis, entryName);
				results.add(result);
				
				// Print result to console
				printResult(result, System.out);
				
				// Write result to file if output file specified
				if (output != null) {
					printResult(result, output);
				}
			}
			
			zis.closeEntry();
		}

		zis.close();
		fis.close();

		// Print summary
		printSummary(results, System.out);
		if (output != null) {
			printSummary(results, output);
		}

		System.out.println("\nTotal files processed: " + fileCount);
		if (outputFilePath != null) {
			System.out.println("Results written to: " + outputFilePath);
		}

	} finally {
		if (output != null) {
			output.close();
		}
	}
}


/* Process a single graph file from zip input stream
 */
private static Result processGraphFile(ZipInputStream zis, String fileName) throws Exception
{	// Read the file content into a list of lines
	List<String> lines = new ArrayList<String>();
	BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
	String line;
	
	while ((line = reader.readLine()) != null) {
		line = line.trim();
		if (!line.isEmpty()) {
			lines.add(line);
		}
	}

	if (lines.isEmpty()) {
		return new Result(fileName, false, "Empty file", null, null);
	}

	// Determine matrix size from first line
	int size = lines.get(0).length();
	if (size < 2) {
		return new Result(fileName, false, "Matrix too small (size < 2)", null, null);
	}

	// Check all lines have same length
	for (int i = 0; i < lines.size(); i++) {
		if (lines.get(i).length() != size) {
			return new Result(fileName, false, 
				"Line " + (i+1) + " has different length", null, null);
		}
	}

	// Check we have enough lines
	if (lines.size() < size) {
		return new Result(fileName, false, 
			"Not enough lines (expected " + size + ", got " + lines.size() + ")", null, null);
	}

	// Create adjacency matrix
	AdjacencyMatrix graph = new AdjacencyMatrix(size);

	// Parse matrix from lines
	for (int r = 0; r < size; r++) {
		String row = lines.get(r);
		for (int c = 0; c < size; c++) {
			char ch = row.charAt(c);
			if (ch == '1') {
				graph.set(r, c, true);
			} else if (ch != '0') {
				return new Result(fileName, false, 
					"Invalid character '" + ch + "' at position (" + r + "," + c + ")", null, null);
			}
		}
	}

	// Check if graph is word-representable
	try {
		Graph orientedGraph = graph.admitsSemiTransitiveOrientation();
		if (orientedGraph != null) {
			return new Result(fileName, true, "Word-representable", null, null);
		} else {
			// Try to get cycles and shortcuts from a fully oriented version
			// First, check if graph is already fully oriented
			if (graph.oriented()) {
				SemiTransitiveReason reason = graph.semiTransitivelyOriented();
				return new Result(fileName, false, "Not word-representable", 
					reason.cycles, reason.shortcuts);
			} else {
				return new Result(fileName, false, "Not word-representable", null, null);
			}
		}
	} catch (Exception e) {
		return new Result(fileName, false, "Error: " + e.getMessage(), null, null);
	}
}


/* Print result to output stream
 */
private static void printResult(Result result, PrintStream out)
{	out.println("========================================");
	out.println("File: " + result.fileName);
	out.println("Word-representable: " + result.isWordRepresentable);
	out.println("Status: " + result.status);
	
	if (!result.isWordRepresentable && result.cycles != null && !result.cycles.isEmpty()) {
		out.println("Cycles found (" + result.cycles.size() + "):");
		for (int i = 0; i < result.cycles.size(); i++) {
			out.println("  " + (i+1) + ". " + result.cycles.get(i));
		}
	}
	
	if (!result.isWordRepresentable && result.shortcuts != null && !result.shortcuts.isEmpty()) {
		out.println("Shortcuts found (" + result.shortcuts.size() + "):");
		for (int i = 0; i < result.shortcuts.size(); i++) {
			out.println("  " + (i+1) + ". " + result.shortcuts.get(i));
		}
	}
	out.println();
}


/* Print result to PrintWriter
 */
private static void printResult(Result result, PrintWriter out)
{	out.println("========================================");
	out.println("File: " + result.fileName);
	out.println("Word-representable: " + result.isWordRepresentable);
	out.println("Status: " + result.status);
	
	if (!result.isWordRepresentable && result.cycles != null && !result.cycles.isEmpty()) {
		out.println("Cycles found (" + result.cycles.size() + "):");
		for (int i = 0; i < result.cycles.size(); i++) {
			out.println("  " + (i+1) + ". " + result.cycles.get(i));
		}
	}
	
	if (!result.isWordRepresentable && result.shortcuts != null && !result.shortcuts.isEmpty()) {
		out.println("Shortcuts found (" + result.shortcuts.size() + "):");
		for (int i = 0; i < result.shortcuts.size(); i++) {
			out.println("  " + (i+1) + ". " + result.shortcuts.get(i));
		}
	}
	out.println();
}


/* Print summary statistics
 */
private static void printSummary(List<Result> results, PrintStream out)
{	int total = results.size();
	int wordRepresentable = 0;
	int notWordRepresentable = 0;
	int errors = 0;
	
	for (Result r : results) {
		if (r.isWordRepresentable) {
			wordRepresentable++;
		} else if (r.status.startsWith("Error") || r.status.contains("Invalid") || 
		           r.status.contains("Empty") || r.status.contains("different length") ||
		           r.status.contains("Not enough")) {
			errors++;
		} else {
			notWordRepresentable++;
		}
	}
	
	out.println("\n========================================");
	out.println("SUMMARY");
	out.println("========================================");
	out.println("Total graphs: " + total);
	out.println("Word-representable: " + wordRepresentable);
	out.println("Not word-representable: " + notWordRepresentable);
	out.println("Errors/Invalid files: " + errors);
	out.println("========================================");
}


/* Print summary statistics
 */
private static void printSummary(List<Result> results, PrintWriter out)
{	int total = results.size();
	int wordRepresentable = 0;
	int notWordRepresentable = 0;
	int errors = 0;
	
	for (Result r : results) {
		if (r.isWordRepresentable) {
			wordRepresentable++;
		} else if (r.status.startsWith("Error") || r.status.contains("Invalid") || 
		           r.status.contains("Empty") || r.status.contains("different length") ||
		           r.status.contains("Not enough")) {
			errors++;
		} else {
			notWordRepresentable++;
		}
	}
	
	out.println("\n========================================");
	out.println("SUMMARY");
	out.println("========================================");
	out.println("Total graphs: " + total);
	out.println("Word-representable: " + wordRepresentable);
	out.println("Not word-representable: " + notWordRepresentable);
	out.println("Errors/Invalid files: " + errors);
	out.println("========================================");
}


/* Inner class to store result for each graph
 */
private static class Result {
	String fileName;
	boolean isWordRepresentable;
	String status;
	List<path.Path> cycles;
	List<path.Path> shortcuts;

	Result(String fileName, boolean isWordRepresentable, String status,
	       List<path.Path> cycles, List<path.Path> shortcuts) {
		this.fileName = fileName;
		this.isWordRepresentable = isWordRepresentable;
		this.status = status;
		this.cycles = cycles;
		this.shortcuts = shortcuts;
	}
}

/*****************/}/********************END CLASS BatchGraphChecker.java***/

