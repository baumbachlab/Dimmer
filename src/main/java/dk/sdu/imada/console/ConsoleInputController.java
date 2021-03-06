package dk.sdu.imada.console;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;

import org.apache.commons.lang3.ArrayUtils;

import com.google.common.io.ByteStreams;

import au.com.bytecode.opencsv.CSVReader;
import dk.sdu.imada.jlumina.core.io.Read450KSheet;
import dk.sdu.imada.jlumina.core.io.ReadControlProbe;
import dk.sdu.imada.jlumina.core.io.ReadIDAT;
import dk.sdu.imada.jlumina.core.io.ReadManifest;
import dk.sdu.imada.jlumina.core.primitives.MSet;
import dk.sdu.imada.jlumina.core.primitives.RGSet;
import dk.sdu.imada.jlumina.core.primitives.USet;
import dk.sdu.imada.jlumina.core.statistics.CellCompositionCorrection;
import dk.sdu.imada.jlumina.core.statistics.Normalization;
import dk.sdu.imada.jlumina.core.statistics.QuantileNormalization;
import dk.sdu.imada.jlumina.core.util.AbstractQualityControl;
import dk.sdu.imada.jlumina.core.util.DataExecutor;
import dk.sdu.imada.jlumina.core.util.QualityControlImpl;
import dk.sdu.imada.jlumina.core.util.RawDataLoader;





public class ConsoleInputController {
	
	private Config config;
	private ConsoleMainController mainController;
	
	private CSVReader reader;
	
	HashMap<String, String[]> columnMap;
	HashMap<Integer, String[]> rowMap;
	
	boolean hasGroupID = false;
	boolean hasPairID = false;
	boolean hasGenderID = false;
	
	ArrayList<String> labelsList;

	public boolean hasGroupID() {
		return hasGroupID;
	}

	public boolean hasPairID() {
		return hasPairID;
	}

	public boolean hasGenderID() {
		return hasGenderID;
	}
	
	
/**
 * This class provides the same functionality as InputController, but without java fx
 * @param config : a Config object needed for parameter settings
 */
	public ConsoleInputController(Config config, ConsoleMainController mainController){
		this.config = config;
		this.mainController = mainController;
	}
	
	/**
	 * starts the file reading process
	 */
	public void start(){
		// checks files
		openLabels();
		// starts preprocessing if no errors 
		pushContinue();
	}
	
	public void openLabels() {
		String path = this.config.getAnnotationPath();
		System.out.println("\nReading annotation file: "+path);
		File f = new File(path);
		try {
			if (!checkAccess(f.getAbsolutePath())) {
				if (checkMandatoryColumns(f.getAbsolutePath())) {
					setMaps(f.getAbsolutePath());
					setLabelsList(f.getAbsolutePath());	
					warning(f.getParentFile().getAbsolutePath()+"/");
					checkVariables();
				}else {
					System.out.println("A problem was found in the header. Check the presence of mandatory fields Sentrix_ID and Sentrix_Position");
				}
			}
		} catch (IOException e) {
			System.out.println("Problems were found in loading " + f.getAbsolutePath());
		}
	}
	
	boolean fileProblem = false;
	private boolean checkAccess(String file) {

		fileProblem = false;

		File f = new File(file);

		if (!f.exists()) {
			System.out.println("File doesn't exist");
			fileProblem = true;
		}else {

			if (!f.canRead()) {
				fileProblem = true;
				System.out.println("The annotation file is not readable, check if this is a valid CSV file or if you have reading permissions");

			}

//			if (!f.canExecute()) {
//				fileProblem = true;
//				System.out.println("The annotation file is not readable, check if this is a valid CSV file or if you have reading permissions");
//			}

		}

		ArrayList<Integer> toksSize = new ArrayList<>();

		try {
			Scanner in = new Scanner(f);
			while(in.hasNextLine()) {
				String line = in.nextLine();
				String toks[] = line.split(",");

				toksSize.add(toks.length);
				if (toks.length <= 1) {
					fileProblem = true;
					System.out.println("The annotation file is not a valid comma-separated file");
					break;
				}
			}
			in.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (!fileProblem) {
			for (int i = 1 ; i < toksSize.size(); i++) {
				if (toksSize.get(i-1) != toksSize.get(i)) {
					fileProblem = true;
					System.out.println("The annotation file is not a valid comma-separated file");
					break;
				}
			}
		}

		return fileProblem;
	}
	
	boolean missingMandatoryColumns;
	private boolean checkMandatoryColumns(String path) throws IOException {

		missingMandatoryColumns = false;
		int count = 0;

		reader = new CSVReader(new FileReader(path));

		for (String cols : reader.readNext()) {

			if (cols.equals("Sentrix_ID") || cols.equals("Sentrix_Position")) { 
				count++;
			}

			if (cols.equals("Group_ID")){
				hasGroupID = true;
			}

			if (cols.equals("Pair_ID")) {
				hasPairID = true;
			}

			if (cols.equals("Gender_ID")) {
				hasGenderID = true;
			}
		}

		missingMandatoryColumns = count<2;	
		return count==2;
	}
	
	private void setMaps(String path) {

		columnMap = new HashMap<String, String[]>();
		rowMap = new HashMap<Integer, String[]>();

		int i = 0;
		try {
			reader = new CSVReader(new FileReader(path));
			String nextLine[] = null;

			while ((nextLine = reader.readNext()) != null ) {
				rowMap.put(i++, nextLine);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		String keys[] = rowMap.get(0);

		for (int j = 0; j < keys.length; j++) {
			String []column = new String[rowMap.size() - 1];
			for (i = 1; i < rowMap.size(); i++) {
				column[i-1] = rowMap.get(i)[j];
			}
			columnMap.put(keys[j], column);
		}
	}
	
	public void setLabelsList(String path) throws IOException {

		labelsList = new ArrayList<String>();

		CSVReader reader = new CSVReader(new FileReader(path));

		String [] str = reader.readNext();

		// Group ID split the file....
		// ParID mapp the pairs ...
		for (String s : str) {
			if (!s.equals("Sentrix_ID") && !s.equals("Sentrix_Position") 
					  && !s.equals("Pair_ID") ) {
				labelsList.add(s);
			}
		}

		reader.close();
	}
	
	boolean missingFiles;
	boolean emptyData;
	boolean duplication;
	private void warning(String basedir) {

		missingFiles = false;
		duplication = false;
		String sID[] = columnMap.get("Sentrix_ID");
		String sPos[] = columnMap.get("Sentrix_Position");

		String names [] = new String[sID.length];
		for (int i = 0; i < sID.length; i++) {
			names[i] = sID[i] + sPos[i];
		}

		for (int i = 0 ; i < names.length - 1; i++) {
			for (int j = i + 1; j < names.length; j++) {
				if (names[i].equals(names[j])) {
					System.out.println("Duplication detected: row " + (i + 1) + " and row " + (j + 1) + " point to the same IDAT file");
					duplication = true;
				}
			}
		}

		for (int i = 0; i < sID.length; i++) {

			String fGreenFile = sID[i] + "_" + sPos[i] + "_Grn" +".idat";
			String fRedFile = sID[i] + "_" + sPos[i] + "_Red" +".idat";

			if (new File(basedir+sID[i]).exists()) {
				fGreenFile = basedir + sID[i] + "/" + fGreenFile;
				fRedFile = basedir + sID[i] + "/" + fRedFile;
				
				
				
			}else {
				fGreenFile = basedir + "/" + fGreenFile;
				fRedFile = basedir + "/" + fRedFile;
			}

			if (!new File(fGreenFile).exists()) {
				System.out.println("The file " + fGreenFile + " was not found. Row " + (i+1));
				missingFiles = true;
			}else {
				File f = new File(fGreenFile);
				if (!f.canRead()) {
					System.out.println("No read permissions to the file " + fGreenFile );
					missingFiles = true;
				}else {
					try {
						byte[] bytes = ByteStreams.toByteArray(new FileInputStream(new File(fGreenFile)));
						String fileType = new String(ArrayUtils.subarray(bytes, 0, 4));

						if (!fileType.equals("IDAT")) {
							fileProblem = true;
							System.out.println(fGreenFile + " is a invalid IDAT file");
						}
					}catch (Exception e){
						fileProblem = true;
						System.out.println(fGreenFile + " is a invalid IDAT file");
					}
				}
			}

			if (!new File(fRedFile).exists()) {
				missingFiles = true;
				System.out.println("The file " + fRedFile +  " was not found. Row " + (i+1));
			}else {
				//checking permission
				File f = new File(fRedFile);
				if (!f.canRead()) {
					System.out.println("No read permissions to the file " + fRedFile );
					missingFiles = true;
				}else {
					try {
						byte[] bytes = ByteStreams.toByteArray(new FileInputStream(new File(fRedFile)));
						String fileType = new String(ArrayUtils.subarray(bytes, 0, 4));

						if (!fileType.equals("IDAT")) {
							fileProblem = true;
							System.out.println(fRedFile + " is a invalid IDAT file");
						}
					}catch (Exception e){
						fileProblem = true;
						System.out.println(fRedFile + " is a invalid IDAT file");
					}
				}
			}
		}
	}
	
	boolean missing_variable;
	boolean missing_confounding_variable;
	public void checkVariables(){
		String variable = this.config.getVariable();
		HashSet<String> confounding_variables = this.config.getConfoundingVariables();
		missing_variable = !this.labelsList.contains(variable);
		missing_confounding_variable = false;
		if(missing_variable){
			System.out.println("The variable of interest ("+variable+") can't be found in the annotation file");
		}
		if(this.config.getModel().equals("Regression")){
			for(String var: confounding_variables){
				if(!this.labelsList.contains(var)){
					missing_confounding_variable = true;
					System.out.println("The confounding variable " + var +" can't be found in the annotation file");
				}
			}
		}
	}
	
	public void pushContinue() {

		if (fileProblem || duplication || missingFiles || missingMandatoryColumns  || missing_variable || missing_confounding_variable) {
			System.out.println("Please, fix your sample annotation file");
			System.exit(0);
		}
		if (this.config.isTTest() && !Util.checkBinary(columnMap.get(config.getVariable()))) {
			System.out.println("Your selected variable of interest must be binary for a T-test");
			System.exit(0);
		}
		if (this.config.getModel().equals("Regression")) {
			if (!checkNumeric()) {
				System.out.println("Your selected coefficients must have numerical values only");
				System.exit(0);
			}
			if(!checkNumeric(columnMap.get(config.getVariable()))){
				System.out.println("Your selected variable of interest must be numerical for regression");
				System.exit(0);
			}
		}
		startPreprocessing();
		
	}
	
	private boolean checkNumeric(String vec[]) {
		try {
			for (String s : vec) {
				Double.parseDouble(s);
			}
		}catch(NumberFormatException e) {
			return false;
		}
		return true;
	}

	private boolean checkNumeric() {

		boolean cond = true;

		for (String s : this.config.getConfoundingVariables()) {
			if(!checkNumeric(columnMap.get(s))){
				cond = false;
			}
		}
		return cond;
	}
	
	public char[] getGenderList() {
		if (hasGenderID) {
			//return columnMap.get("Gender_ID")
			char ids[] = new char [columnMap.get("Gender_ID").length];
			int i = 0;
			for (String s : columnMap.get("Gender_ID")) {
				if (s.equals("1")) {
					ids[i++] = 'F';
				}else {
					ids[i++] = 'M';
				}
			}
			return ids;
		}else {
			return null;
		}
	}
	
	public HashMap<String,String[]> getColumnMap(){
		return this.columnMap;
	}
	
	
	////////////////////////////////////////// Raw data loader execution //////////////////////////////////////
	
	ReadManifest manifest;
	ReadControlProbe readControlProbe;
	RGSet rgSet;
	MSet mSet, mRefSet;
	USet uSet, uRefSet;
	Normalization normalizations;
	CellCompositionCorrection cellCompositionCorrection;
	RawDataLoader rawDataLoader;
	ConsoleInputMonitor inputFilesMonitor;
	int maxCoreSteps;
	int stepsDone;
	AbstractQualityControl qualityControl;
	boolean performBackgroundCorrection;
	boolean performProbeFiltering;

	private void startPreprocessing() {

		stepsDone = 0;

		testDataType();
		initializeJLuminaCore();
		rawDataLoader = new RawDataLoader(rgSet, manifest, readControlProbe, uSet, mSet,
				cellCompositionCorrection, uRefSet, mRefSet, normalizations, this.config.getThreads(), getGenderList());

		rawDataLoader.setMaxSteps(maxCoreSteps);
		rawDataLoader.setQualityControl(qualityControl);
		rawDataLoader.setPerformBackgroundCorrection(performBackgroundCorrection);
		rawDataLoader.setPerformProbeFiltering(performProbeFiltering);
		
		DataExecutor dataExecutor = new DataExecutor(rawDataLoader);
		inputFilesMonitor = new ConsoleInputMonitor(rawDataLoader, mainController);

		Thread loaderThread = new Thread(dataExecutor);
		loaderThread.start();
		inputFilesMonitor.run();
	}

	private void testDataType() {
		String idatFilePath = new Read450KSheet(this.config.getAnnotationPath()).getBaseName()[0] + "_Grn.idat";

		ReadIDAT gIdat = new ReadIDAT();
		gIdat.readNonEncryptedIDAT(idatFilePath);
		int v = gIdat.getnSNPsRead();

		if (v == 622399) {
			mainController.setInfinium(true);
			mainController.setEpic(false);
		}else {
			mainController.setInfinium(false);
			mainController.setEpic(true);
		}
	}

	private void initializeJLuminaCore() {

		maxCoreSteps = 4;

		this.rgSet = new RGSet(config.getAnnotationPath());

		String mf = null;
		String mfProbes = null;
		if (mainController.isInfinium()) { 

			System.out.println("Using infinium data type");
			mf = "resources/manifest_summary.csv";
			mfProbes = "resources/illumminaControl.csv";

			if (getClass().getClassLoader().getResourceAsStream(mf)==null) {
				mf = "manifest_summary.csv";
				mfProbes = "illumminaControl.csv";	
			}

		}else {
			System.out.println("Using epic data type");
			mf = "resources/epic_manifest.csv";
			mfProbes = "resources/illumminaControl.csv";

			if (getClass().getClassLoader().getResourceAsStream(mf)==null) {
				mf = "epic_manifest.csv";
				mfProbes = "illumminaControl.csv";
			}
		}

		this.manifest = new ReadManifest(mf);
		this.readControlProbe = new ReadControlProbe(mfProbes);
		this.uSet = new USet();
		this.uRefSet = new USet();
		this.mSet = new MSet();
		this.mRefSet = new MSet();
		this.qualityControl = new QualityControlImpl(rgSet, manifest, readControlProbe);

		normalizations = new QuantileNormalization(); 

		if (mainController.isInfinium()) {
			if (this.config.getCellComposition()) {
				cellCompositionCorrection = new CellCompositionCorrection();
				maxCoreSteps = 9;
			}else {
				cellCompositionCorrection = null;
			}
			performBackgroundCorrection = config.getBackgroundCorrection();
			performProbeFiltering = config.getProbeFiltering();
		}else {
			System.out.println("Cell composition estimation, background correction and probe filtering are not avaliable for epic data");
			cellCompositionCorrection = null;	
			performBackgroundCorrection = false;
			performProbeFiltering = false;
		}
	}


}
