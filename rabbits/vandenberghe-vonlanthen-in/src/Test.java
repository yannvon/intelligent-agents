import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class Test {

	public static void main(String[] args) {
		BufferedImage im=null;
		try {
			im =  ImageIO.read(new File("images/bunny.png"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		BufferedImage image= im;
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				JFrame editorFrame = new JFrame("QR Code");
				editorFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	
				ImageIcon imageIcon = new ImageIcon(image);
				JLabel jLabel = new JLabel();
				jLabel.setIcon(imageIcon);
				editorFrame.getContentPane().add(jLabel, BorderLayout.CENTER);
	
				editorFrame.pack();
				editorFrame.setLocationRelativeTo(null);
				editorFrame.setVisible(true);
			}
		});
	System.out.println(im);

	}

}
