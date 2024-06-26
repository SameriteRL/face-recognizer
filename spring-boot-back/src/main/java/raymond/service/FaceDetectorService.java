package raymond.service;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_objdetect.FaceDetectorYN;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Utilizes the YuNet deep neural network face detection model. Thank you
 * Professor Shiqi Yu and Yuantao Feng! <p>
 * 
 * https://github.com/opencv/opencv_zoo/tree/master/models/face_detection_yunet
 * <p>
 * 
 * Note that not all image formats are supported for facial recognition due to
 * limitations of the {@code cv::imread()} function. You can find a list
 * of supported formats here: <p>
 * 
 * https://docs.opencv.org/4.x/d4/da8/group__imgcodecs.html
 */
@Service
public class FaceDetectorService {

    @Value("${app.service.facedetectorpath}")
    private String faceDetectorModelPath;

    /**
     * Creates a new FaceDetectorYN object. Make sure to set the input size
     * accordingly using {@code FaceDetectorYN.setInputSize()} before using it
     * on an image. <p>
     * 
     * It is the caller's responsibility to later deallocate the face detector
     * properly.
     * 
     * @return A new FaceDetectorYN object with an input size of 0px x 0px.
     */
    public FaceDetectorYN createFaceDetector() {
        FaceDetectorYN faceDetector = null;
        try (Size detectorSize = new Size()) {
            faceDetector =
                FaceDetectorYN.create(faceDetectorModelPath, "", detectorSize);
        }
        return faceDetector;
    }

    /**
     * Sets the input size of the FaceDetectorYN object.
     * 
     * @param faceDetector Face detector to configure.
     * @param width The width of the image for the detector to be used on.
     * @param height The height of the image for the detector to be used on.
     */
    public void setDetectorInputSize(
        FaceDetectorYN faceDetector,
        int width,
        int height
    ) {
        try (Size detectorSize = new Size(width, height)) {
            faceDetector.setInputSize(detectorSize);
        }
    }

    /**
     * Allocates and returns a Mat containing face detection data after
     * performing face detection on an image. See
     * {@link #detectFaces(Mat, String)} for return value format. <p>
     * 
     * It is the caller's responsibility to properly deallocate the
     * returned Mat. <p>
     * 
     * Note that not all image formats are supported; see {@link #FaceDetector}
     * for details.
     * 
     * @param imgBytes Byte array of the image to detect faces from.
     * @param faceDetector YuNet face detector model.
     * @throws NullPointerException If any arguments are null.
     * @throws IllegalArgumentException If the face detector path is invalid.
     * @throws IOException If the image is empty or invalid, or for general I/O
     *                     errors.
     */
    public Mat detectFaces(
        byte[] imgBytes,
        FaceDetectorYN faceDetector
    ) throws IOException {
        if (imgBytes == null) {
            throw new NullPointerException("Image byte array");
        }
        File tempInputFile = File.createTempFile("tempInputFile", null);
        try (OutputStream fileOutStream = new FileOutputStream(tempInputFile)) {
            fileOutStream.write(imgBytes);
            fileOutStream.flush();
            return detectFaces(tempInputFile.getAbsolutePath(), faceDetector);
        }
        finally {
            if (tempInputFile != null) {
                tempInputFile.delete();
            }
        }
    }

    /**
     * Allocates and returns a Mat containing face detection data after
     * performing face detection on an image. See
     * {@link #detectFaces(Mat, String)} for return value format. <p>
     * 
     * It is the caller's responsibility to properly deallocate the
     * returned Mat. <p>
     * 
     * Note that not all image formats are supported; see {@link #FaceDetector}
     * for details.
     * 
     * @param imgPath Path of the image to detect faces from.
     * @param faceDetector YuNet face detector model.
     * @throws NullPointerException If any arguments are null.
     * @throws IllegalArgumentException If the face detector path is invalid.
     * @throws IOException If the image is empty or invalid, or for general I/O
     *                     errors.
     */
    public Mat detectFaces(
        String imgPath,
        FaceDetectorYN faceDetector
    ) throws IOException {
        if (imgPath == null) {
            throw new NullPointerException("Image path");
        }
        Mat imgMat = null;
        try {
            imgMat = imread(imgPath);
            return detectFaces(imgMat, faceDetector);
        }
        finally {
            if (imgMat != null) {
                imgMat.deallocate();
            }
        }
    }

    /**
     * Allocates and returns a Mat containing face detection data after
     * performing face detection on an image. <p>
     * 
     * It is the caller's responsibility to properly deallocate the
     * returned Mat. <p>
     * 
     * Note that not all image formats are supported; see {@link #FaceDetector}
     * for details.
     * 
     * @param imgMat Image Mat to detect faces from.
     * @param faceDetector YuNet face detector model.
     * @return A 2D Mat of shape [num_faces, 15]
     *         <ul>
     *           <li> 0-1:   x, y of bounding box top left corner
     *           <li> 2-3:   width, height of bbox
     *           <li> 4-5:   x, y of right eye
     *           <li> 6-7:   x, y of left eye
     *           <li> 8-9:   x, y of nose tip
     *           <li> 10-11: x, y of right corner of mouth
     *           <li> 12-13: x, y of left corner of mouth
     *           <li> 14:    face score
     *         </ul>
     * @throws NullPointerException If any arguments are null.
     * @throws IllegalArgumentException If the face detector path is invalid.
     * @throws IOException If the image is empty or invalid, or for general I/O
     *                     errors.
     */
    public Mat detectFaces(
        Mat imgMat,
        FaceDetectorYN faceDetector
    ) throws IOException {
        if (imgMat == null) {
            throw new NullPointerException("Image Mat");
        }
        if (faceDetector == null) {
            throw new NullPointerException("Face detector model");
        }
        if (imgMat.data() == null || imgMat.rows() <= 0 || imgMat.cols() <= 0) {
            throw new IOException("Invalid image Mat");
        }
        Mat facesMat = null;
        Size detectSize = null;
        try {
            facesMat = new Mat();
            detectSize = new Size(imgMat.cols(), imgMat.rows());
            faceDetector.setInputSize(detectSize);
            faceDetector.detect(imgMat, facesMat);
        }
        catch (Exception e) {
            if (facesMat != null) {
                facesMat.deallocate();
            }
            throw e;
        }
        finally {
            if (detectSize != null) {
                detectSize.deallocate();
            }
        }
        return facesMat;
    }
}
