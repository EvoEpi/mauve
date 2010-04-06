package org.gel.mauve.assembly;

import java.awt.BorderLayout;

import java.awt.Button;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import org.gel.mauve.BaseViewerModel;
import org.gel.mauve.Genome;
import org.gel.mauve.LCB;
import org.gel.mauve.XmfaViewerModel;
import org.gel.mauve.analysis.Gap;
import org.gel.mauve.analysis.PermutationExporter;
import org.gel.mauve.analysis.SNP;
import org.gel.mauve.analysis.SnpExporter;
import org.gel.mauve.dcjx.DCJ;

public class ScoreAssembly extends JFrame{
	
	private static String SUM_CMD = "Summary";
	private static String SUM_DESC = "Summary of scoring assembly";
	private static String SNP_CMD = "SNPs";
	private static String SNP_DESC = "SNPs between reference and assembly";
	private static String GAP_CMD = "Gaps";
	private static String GAP_DESC = "Gaps in reference and assembly";
	
	private static int A = 0;
	
	private static int C = 1;
	
	private static int T = 2;
	
	private static int G = 3;
	
	private static final String temp = "Running...";
	
	private static final String error = "Error computing DCJ distances! Please report bug to atritt@ucdavis.edu";
	
	private TextArea sumTA, snpTA, gapTA;
	
	private int fWIDTH = 400;

	private int fHEIGHT = 400;
	
	private Panel toptopPanel;

	private CardLayout cards;

	private String box;
	
	private AssemblyScorer assScore;

	private JFrame frame;
	
	private static final String USAGE = 
		"Usage: java -cp Mauve.jar org.gel.mauve.assemlbly.ScoreAssembly [options]\n" +
		"  where [options] are:\n" +
		"\t--alignment <path>\n\t\tthe alignment of the assembly to the reference to score\n" +
//		"\t--reference <path>\n\t\tthe reference genome\n" +
//		"\t--assembly <path>\n\t\tthe assembly to score\n" +
		"\t--outDir <path>\n\t\tthe directory to store output in\n" +
		"\t\tIf this is not set, the current working directory is used.\n" +
		"\t--batch\n\t\ta flag to indicate running in batch mode.\n" +
		"\t\tIf you call this flag, summary info will be printed in tab-delimited\n" +
		"\t\tformat to standard output without any header text.\n" +
		"\t--help\n\t\tprint this text.\n";
	
	public static void main(String[] args){
		if (args.length == 0 || args[0].equalsIgnoreCase("--help") ){
			System.err.println(USAGE);
			System.exit(-1);
		}
		
		RunTimeParam rtp = null;
		try {
			rtp = new RunTimeParam(args);
		} catch (Exception e){
			if (e.getMessage().equals(RunTimeParam.DUAL_MODE)){
				System.err.println("You gave me an alignment along with a reference and/or assembly genome.\n" +
						"Do not use use the \"--reference\" and \"--assembly\" flags with the \"--alignment\" flag.");
				 
			} else if (e.getMessage().startsWith(RunTimeParam.UNREC_ARG)){
				System.err.println("Unrecognized argument: " + 
						e.getMessage().substring(RunTimeParam.UNREC_ARG.length()));

				System.err.println(USAGE);
			} else if (e.getMessage().equals(RunTimeParam.HELP)){
				System.err.println(USAGE);
			} else {
				e.printStackTrace();
			}
			System.exit(-1);
		}
		
		AssemblyScorer sa = null;
		String basename = rtp.basename;
		File outDir = rtp.outDir;		
		try {
			//sa = new ScoreAssembly(args,true);
			XmfaViewerModel model = new XmfaViewerModel(rtp.alnmtFile,null);
			sa = new AssemblyScorer(model);
		} catch (Exception e){
			e.printStackTrace();
			System.exit(-1);
		}

		
		AssemblyScorer.printInfo(sa,outDir,basename,rtp.batch);
		
	} 
	
	private static class RunTimeParam{
		static String UNREC_ARG = "unrecognized argument";
		/**
		 * If the user passes in both an alignment and
		 * the reference and/or assembly genomes.
		 */
		static String DUAL_MODE = "dual mode";
		static String HELP = "help";
		
		String basename;
		File outDir;
		boolean batch = false;
		
		boolean alnmtSet = false;
		String alnmtPath;
		File alnmtFile;
		
		boolean refSet = false;
		String refPath;
		File refFile;
		boolean assSet = false;
		String assPath;
		File assFile;
		
		
		RunTimeParam(String[] args) throws Exception {
			int i = 0;
			while (i < args.length){
				if (args[i].equalsIgnoreCase("--alignment")){
					i++;
					alnmtSet = true;
					if (refSet || assSet)
						throw new Exception(DUAL_MODE);
					alnmtPath = args[i];
					i++;
				} else if (args[i].equalsIgnoreCase("--reference")){
					i++;
					refSet = true;
					if (alnmtSet)
						throw new Exception(DUAL_MODE);
					refPath = args[i];
					i++;
				} else if (args[i].equalsIgnoreCase("--assembly")){
					i++;
					assSet = true;
					if (alnmtSet)
						throw new Exception(DUAL_MODE);
					assPath = args[i];
					i++;
				} else if (args[i].equalsIgnoreCase("--outDir")){
					i++;
					outDir = new File(args[i]);
					if (!outDir.exists()){
						outDir.mkdir();
					}
					i++;
				} else if (args[i].equalsIgnoreCase("--batch")){
					batch = true;
					i++;
				} else if (args[i].equalsIgnoreCase("--help")){
					throw new Exception(HELP);
				} else {
					throw new Exception (UNREC_ARG+args[i]);
				}
			}
			
			if (outDir == null){
				outDir = new File(System.getProperty("user.dir"));
			}
			
			if (alnmtSet){
				basename = alnmtPath.substring(alnmtPath.lastIndexOf("/")+1,alnmtPath.lastIndexOf("."));
				alnmtFile = new File(alnmtPath);
			} else if (refSet && assSet){
				
			} else {
				System.err.println("Bad RunTimeParam.class");
			}
			
			
		}
		
	}
	

	public ScoreAssembly(XmfaViewerModel model){
		build(model);
		sumTA.append(temp);
		snpTA.append(temp);
		gapTA.append(temp);
		assScore = new AssemblyScorer(model);
		
		sumTA.replaceRange("", 0, temp.length());
		setSumText(assScore,true,false);
		snpTA.replaceRange("", 0, temp.length());
		gapTA.replaceRange("", 0, temp.length());
		setInfoText(assScore);
	}

	private void setInfoText(AssemblyScorer as){
		StringBuilder sb = new StringBuilder();
		sb.append("SNP_Pattern\tRef_Contig\tRef_PosInContig\tRef_PosGenomeWide\tAssembly_Contig\tAssembly_PosInContig\tAssembly_PosGenomeWide\n");
		SNP[] snps = as.getSNPs();
		for (int i = 0; i < snps.length; i++)
			sb.append(snps[i].toString()+"\n");
		
		snpTA.setText(sb.toString());
		
		sb = new StringBuilder();
		sb.append("Sequence\tContig\tPosition_in_Contig\tGenomeWide_Position\tLength\n");
		Gap[] gaps = as.getReferenceGaps();
		for (int i = 0; i < gaps.length; i++)
			sb.append(gaps[i].toString("reference")+"\n");
		gaps = as.getAssemblyGaps();
		for (int i = 0; i < gaps.length; i++)
			sb.append(gaps[i].toString("assembly")+"\n");
		gapTA.setText(sb.toString());
		
	}
	
	private void setSumText(AssemblyScorer as, boolean header, boolean singleLine){
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMaximumFractionDigits(4);
		StringBuilder sb = new StringBuilder();
		if (singleLine){
			if (header) {
				sb.append("DCJ_Distance\tNum_Blocks\tNum_SNPs\tNumGaps_Ref\tNumGaps_Assembly\t" +
						"TotalBasesMissed\tPercentBasesMissed\tExtraBases\tPercentExtraBases\n");
			}
			
			sb.append(as.getDCJDist()+"\t"+as.numBlocks()+"\t"+as.getSNPs().length+"\t"+
						as.getReferenceGaps().length+"\t"+as.getAssemblyGaps().length+"\t"+
					 	as.totalMissedBases()+"\t"+nf.format(as.percentMissedBases()*100)+"\t"+
					 	as.totalExtraBases()+"\t"+nf.format(as.percentExtraBases()*100)+"\n");
			
		} else {
			if (header)
				sb.append("DCJ Distance:\t"+as.getDCJDist()+"\n"+
					 "Number of Blocks:\t"+as.numBlocks()+"\n"+
					 "Number of SNPs:\t"+as.getSNPs().length+"\n"+
					 "Number of Gaps in Reference:\t"+as.getReferenceGaps().length+"\n"+
					 "Number of Gaps in Assembly:\t"+as.getAssemblyGaps().length+"\n" +
					 "Total bases missed:\t" + as.totalMissedBases() +"\n"+
					 "Percent bases missed:\t" + nf.format(as.percentMissedBases()*100)+" %\n"+
					 "Total bases extra:\t" + as.totalExtraBases()+"\n" +
					 "Percent bases extra:\t" + nf.format(as.percentExtraBases()*100)+ " %\n");
			else 
				sb.append(as.getDCJDist()+"\n"+
						 as.numBlocks()+"\n"+
						 as.getSNPs().length+"\n"+
						 as.getReferenceGaps().length+"\n"+
						 as.getAssemblyGaps().length+"\n" +
						 as.totalMissedBases() +"\n"+
						 nf.format(as.percentMissedBases()*100)+"\n"+
						 as.totalExtraBases()+"\n" +
						 nf.format(as.percentExtraBases()*100)+ "\n");
			
		}
		
		sumTA.setText(sb.toString());
	}
	
	
	private void build (XmfaViewerModel model) {
		frame = new JFrame ("Assembly Score - " + model.getSrc().getName());
		
		
		
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		int xPos = dim.width - fWIDTH;
		frame.setSize (fWIDTH, fHEIGHT);
		frame.setLocation(xPos, 0);
		JPanel content = new JPanel (new BorderLayout ());
		frame.getContentPane ().add (content, BorderLayout.CENTER);
		box = "";
		setLayout (new BorderLayout ());
		// /create top panel
		Panel topPanel = new Panel ();

		topPanel.setLayout (new BorderLayout ());
		// top top panel with cards
		cards = new CardLayout ();
		toptopPanel = new Panel ();
		toptopPanel.setLayout (cards);
		topPanel.add (toptopPanel, BorderLayout.CENTER);
		
		// top lower panel of buttons
		Panel toplowerPanel = new Panel ();
		GridLayout butts = new GridLayout (1, 0);
		butts.setHgap (50);
		toplowerPanel.setLayout (butts);
		topPanel.add (toplowerPanel, BorderLayout.SOUTH);
		// make buttons
		Button Bsum = new Button (SUM_CMD);
		Button Bsnps = new Button (SNP_CMD);
		Button Bgaps = new Button (GAP_CMD);
		toplowerPanel.add (Bsum);
		toplowerPanel.add (Bsnps);
		toplowerPanel.add (Bgaps);
		// make listener
		ChangeCards cc = new ChangeCards ();
		// register buttons
		Bsum.addActionListener (cc);
		Bsnps.addActionListener (cc);
		Bgaps.addActionListener (cc);
		// /add the top panel
		add (topPanel, BorderLayout.NORTH);
		content.add (topPanel, BorderLayout.CENTER);

		Font font = new Font ("monospaced", Font.PLAIN, 12);
		font.getSize2D();
		
		// /Add output text to cards panel
		sumTA = new TextArea (box, 25, 160);
		sumTA.setEditable (false);
		sumTA.setFont (font);
		toptopPanel.add (SUM_DESC, sumTA);
		
		cards.show (toptopPanel, SUM_DESC);
		// /Add DCJ Operations text to cards panel
		snpTA = new TextArea (box, 25, 160);
		snpTA.setEditable (false);
		snpTA.setFont (font);
		toptopPanel.add (SNP_DESC, snpTA);
		// /Add log text area
		gapTA = new TextArea (box, 25, 160);
		gapTA.setEditable (false);
		gapTA.setFont (font);
		toptopPanel.add (GAP_DESC, gapTA);
		sumTA.setText ("");
		frame.setVisible (true);
	}

	
	/**
	 * 
	 * @param refPath
	 * @param assPath
	 * @param reorder
	 * @return
	 */
	public static File runPMauveAlnmt(String refPath, String assPath, String outDirPath, boolean reorder){
		if (reorder){
			
		} else {
			File ref = new File(refPath);
			File ass = new File(assPath);
			File outDir = new File(outDirPath);
			
			if (!outDir.exists()){
				outDir.mkdir();
			} else if (!outDir.isDirectory()){
				System.err.println(outDirPath + " already exists as a file.");
				System.exit(-1);
			} 
		}
		return null;
	}
	
	/**
	 * Returns a 4x4 matrix of counts of substitution types between 
	 * genome <code>src_i</code> and <code>src_j</code>
	 * 
	 * <code>
	 *      A  C  T  G
	 *    A -
	 *    C    -
	 *    T       -
	 *    G          -
	 * </code>
	 * 
	 * @param snps
	 * @return a 4x4 matrix of substitution counts
	 */
	public static int[][] countSubstitutions(SNP[] snps){
		int[][] subs = new int[4][4];
		for (int k = 0; k < snps.length; k++){ 
			char c_0 = snps[k].getChar(0);
			char c_1 = snps[k].getChar(1);
			
			try {
			if (c_0 != c_1)
				subs[getBaseIdx(c_0)][getBaseIdx(c_1)]++;
			} catch (IllegalArgumentException e){
				System.err.println("Skipping ambiguity: ref = " +c_0 +" assembly = " + c_1 );
			}
		}
		return subs;
	}
	
	private static int getBaseIdx(char c) throws IllegalArgumentException {
		switch(c){
		  case 'a': return A; 
		  case 'A': return A;
		  case 'c': return C;
		  case 'C': return C;
		  case 't': return T;
		  case 'T': return T;
		  case 'g': return G;
	 	  case 'G': return G;
		  default:{ throw new IllegalArgumentException("char " + c);}
		}
	}
	
	public static void launchWindow(BaseViewerModel model){
		ScoreAssembly sa = null;
		if (model instanceof XmfaViewerModel) {
			System.out.println("Scoring assembly " + model.getSrc().getName());
			sa = new ScoreAssembly((XmfaViewerModel)model);
		} else {
			System.err.println("Can't score assembly -- Please report this bug!");
		}
		
	}
	
	private class ChangeCards implements ActionListener {
		public void actionPerformed (ActionEvent e) {
			if (e.getActionCommand () == SUM_CMD) {
				cards.show (toptopPanel, SUM_DESC);
			}
			if (e.getActionCommand () == SNP_CMD) {
				cards.show (toptopPanel, SNP_DESC);
			}
			if (e.getActionCommand () == GAP_CMD) {
				cards.show (toptopPanel, GAP_DESC);
			}
		}// end actionPerformed
	}// end ChangeCards

	

	 private static OutputStream ta2os(final TextArea t)
	    { return new OutputStream()
	       { TextArea ta = t;
	         public void write(int b) //minimum implementation of an OutputStream
	          { byte[] bs = new byte[1]; bs[0] = (byte) b;
	            ta.append(new String(bs));
	          }//write
	       };
	    }
	
	
}