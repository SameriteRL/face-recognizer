package raymond.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_objdetect.FaceRecognizerSF;
import org.springframework.stereotype.Service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

import raymond.classes.FaceBox;
import raymond.utils.StringUtils;

@Service
public class ImageService {

    /**
     * Allocates and returns a face feature Mat of an image. Requires a SFace
     * face recognizer, as well as a set of face box coordinates and dimensions
     * typically determined using the YuNet face detection model elsewhere.
     * <p>
     * 
     * The original Mat is not modified or deallocated as a result of the
     * operation; it is the responsibility of the caller to later deallocate it
     * as well as the returned Mat.
     * 
     * @param recognizerModelPath Path of the SFace face recognizer model.
     * @param srcImg Source image, typically the whole original image.
     * @param fBox Face box coordinates and dimensions on the source image,
     *             typically one row of a Mat.
     * @return The feature Mat of the image.
     */
    public Mat getFeatureMat(
        String recognizerModelPath,
        Mat srcImg,
        Mat faceBox
    ) {
        if (recognizerModelPath == null) {
            throw new NullPointerException("Face recognizer model path");
        }
        try (FaceRecognizerSF fr =
                FaceRecognizerSF.create(recognizerModelPath, "")
        ) {
            return getFeatureMat(fr, srcImg, faceBox);
        }
    }

    /**
     * Allocates and returns a face feature Mat of an image. Requires a SFace
     * face recognizer, as well as a set of face box coordinates and dimensions
     * typically determined using the YuNet face detection model elsewhere.
     * <p>
     * 
     * The original Mat is not modified or deallocated as a result of the
     * operation; it is the responsibility of the caller to later deallocate it
     * as well as the returned Mat.
     * 
     * @param fr SFace face recognizer model.
     * @param srcImg Source image, typically the whole original image.
     * @param fBox Face box coordinates and dimensions on the source image,
     *             typically one row of a Mat.
     * @return The feature Mat of the image.
     */
    public Mat getFeatureMat(
        FaceRecognizerSF fr,
        Mat srcImg,
        Mat faceBox
    ) {
        if (fr == null) {
            throw new NullPointerException("Face recognizer model");
        }
        if (srcImg == null) {
            throw new NullPointerException("Source image Mat");
        }
        if (faceBox == null) {
            throw new NullPointerException("Face box Mat");
        }
        Mat alignedMat = null, featureMat = null;
        try {
            alignedMat = new Mat();
            featureMat = new Mat();
            fr.alignCrop(srcImg, faceBox, alignedMat);
            fr.feature(alignedMat, featureMat);
            // Don't know why this has to be cloned, it just does
            Mat featureMatClone = featureMat.clone();
            return featureMatClone;
        }
        finally {
            if (alignedMat != null) {
                alignedMat.deallocate();
            }
            if (featureMat != null) {
                featureMat.deallocate();
            }
        }
    }

    /**
     * Visualizes face recognition results by drawing bounding boxes onto a
     * test image. Returns a byte array representation of the resulting image.
     * 
     * @param imgPath Path of the image to draw onto.
     * @param roiList List of ROIs to draw onto the image.
     * @return A byte array representing the resulting image.
     * @throws IOException If a format cannot be determined from the image file
     *                     name, or for general I/O errors.
     */
    public byte[] visualizeBoxes(
        String imgPath,
        List<FaceBox> roiList
    ) throws IOException {
        String imgFormat = StringUtils.getExtension(imgPath);
        BufferedImage bufImg = createBufferedImage(imgPath);
        drawBoxes(bufImg, roiList, false);
        ByteArrayOutputStream imgOutStream = new ByteArrayOutputStream();
        ImageIO.write(bufImg, imgFormat, imgOutStream);
        return imgOutStream.toByteArray();
    }

    /**
     * Creates a BufferedImage from an image path and corrects its orientation,
     * if necessary. <p>
     * 
     * Because ImageIO.read() does not read image metadata, photos that were
     * rotated on smartphones might be loaded in with their original
     * orientation. This function checks the image's orientation metadata and
     * corrects the image if necessary, resulting in a BufferedImage that
     * matches the orientation you see in the file system. <p>
     * 
     * If metadata cannot be determined from the image, an unmodified
     * BufferedImage is returned. <p>
     * 
     * @param imgPath Path to the image file.
     * @return BufferedImage with the correct orientation.
     * @throws NullPointerException If the image path is null.
     * @throws IOException If the stream is invalid.
     */
    public BufferedImage createBufferedImage(
        String imgPath
    ) throws IOException {
        if (imgPath == null) {
            throw new NullPointerException("Image path is null");
        }
        File imgFile = new File(imgPath);
        BufferedImage img = ImageIO.read(imgFile);
        if (img == null) {
            throw new IOException("Stream could not be read as an image");
        }
        int orientation = -1;
        // Returns unmodified image if EXIF orientation cannot be determined
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(imgFile);
            ExifIFD0Directory exifIFD0 =
                metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            orientation = exifIFD0.getInt(ExifIFD0Directory.TAG_ORIENTATION);
        }
        catch (Exception e) {
            return img;
        }
        int rotateDegrees = -1;
        switch (orientation) {
            // Image is oriented normally
            case 1:
                return img;
            // Right side, top (rotate 90 degrees CW)
            case 6:
                rotateDegrees = 90;
                break;
            // Bottom, right side (rotate 180 degrees)
            case 3:
                rotateDegrees = 180;
                break;
            // Left side, bottom (rotate 270 degrees CW)
            case 8:
                rotateDegrees = 270;
                break;
        }
        // Rotates the image if orientation is incorrect
        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage rotatedImage = new BufferedImage(
            height,
            width,
            img.getType()
        );
        Graphics2D g2d = rotatedImage.createGraphics();
        AffineTransform transform = new AffineTransform();
        transform.translate(height / 2, width / 2);
        transform.rotate(Math.toRadians(rotateDegrees));
        transform.translate(-width / 2, -height / 2);
        g2d.setTransform(transform);
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();
        return rotatedImage;
    }

    /**
     * Helper method to draw red rectangular boxes onto an image for each ROI
     * in the given list. The image is modified in-place. <p>
     * 
     * The X and Y coordinates marks the top left corner of each frame. The
     * width corresponds to the frame's size along the positive X-axis, and the
     * length corresponds to the size along the negative Y-axis. <p>
     * 
     * If {@code debug == true}, the "confidence" score associated with each
     * frame is also drawn.
     * 
     * @param img Image to draw onto.
     * @param roiList List of FaceBox objects.
     * @param debug Enable or disable debug mode.
     * @throws NullPointerException If any arguments are null.
     */
    private void drawBoxes(
        BufferedImage img,
        List<FaceBox> roiList,
        boolean debug
    ) {
        if (img == null) {
            throw new NullPointerException("Image is null");
        }
        if (roiList == null) {
            throw new NullPointerException("ROI list is null");
        }
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(Color.RED);
        // Stroke and font size proportional to length of img's smallest side
        int length = (img.getWidth() < img.getHeight())
            ? img.getWidth() : img.getHeight();
        int rectStrokeSize = length / 300;
        int fontSize = rectStrokeSize * 10;
        g2d.setStroke(
            new BasicStroke(
                rectStrokeSize,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
                3f,
                null,
                0f
            )
        );
        g2d.setFont(new Font("Arial", Font.PLAIN, fontSize));
        for (FaceBox roi: roiList) {
            // X and Y coords indicate top left of rectangle
            g2d.drawRect(
                roi.xCoord,
                roi.yCoord,
                roi.width,
                roi.height
            );
            // Labels are drawn directly above rectangle
            if (debug) {
                g2d.drawString(
                    String.format("%s : %.3f", roi.label, roi.predictScore),
                    roi.xCoord,
                    roi.yCoord - fontSize / 2
                );
                continue;
            }
            g2d.drawString(roi.label, roi.xCoord, roi.yCoord - fontSize / 2);
        }
        g2d.dispose();
    }
}