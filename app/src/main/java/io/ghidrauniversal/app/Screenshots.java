package io.ghidrauniversal.app;

import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/**
 * Saves a JavaFX {@link Scene} to a PNG without depending on the javafx.swing
 * module: snapshot to a {@link WritableImage}, copy pixels into an AWT
 * {@link BufferedImage}, and write with {@link ImageIO}. Used for headless
 * validation under Xvfb. Must be called on the JavaFX application thread.
 */
final class Screenshots {

    static void save(Scene scene, Path target) {
        try {
            WritableImage image = scene.snapshot(null);
            int w = (int) image.getWidth();
            int h = (int) image.getHeight();
            BufferedImage bimg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            PixelReader reader = image.getPixelReader();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    bimg.setRGB(x, y, reader.getArgb(x, y));
                }
            }
            ImageIO.write(bimg, "png", target.toFile());
            System.out.println("screenshot written: " + target + " (" + w + "x" + h + ")");
        } catch (Exception e) {
            System.err.println("screenshot failed: " + e);
        }
    }

    private Screenshots() {}
}
