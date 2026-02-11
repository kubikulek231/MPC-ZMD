package graphics;

import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.imageio.ImageIO;

import Core.FileBindings;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Pair;

public class Dialogs {
    private static ArrayList<Stage> openStages = new ArrayList<>();

    /**
     * Show buffered image in new window.
     *
     * @param bufferedImage
     * @param title
     */
    public static void showImageInWindow(BufferedImage bufferedImage, String title) {
        showImageInWindow(bufferedImage, title, false);
    }

    /**
     * Show buffered image in new window.
     *
     * @param bufferedImage
     * @param title
     * @param keepOpen      If it is set to true, window can be close only manually. Close All button wont work on it.
     */
    public static void showImageInWindow(BufferedImage bufferedImage, String title, boolean keepOpen) {
        if (bufferedImage == null) return;

        Stage stage = new Stage();

        stage.setTitle(String.format("[%dx%d] %s", bufferedImage.getWidth(), bufferedImage.getHeight(), title));
        stage.getIcons().add(FileBindings.favicon);

        BorderPane root = new BorderPane();

        Scene scene = new Scene(root);
        StackPane stackPane = setImageStackPane(bufferedImage, title, scene, true, bufferedImage.getWidth(), bufferedImage.getHeight());
        ImageView imageView = (ImageView) stackPane.getChildren().get(0);
        root.setCenter(stackPane);
        stage.setScene(scene);

        stage.setUserData(keepOpen);

        stage.setOnCloseRequest((e) -> openStages.remove(stage));
        openStages.add(stage);

        setUpSizeListener(imageView, bufferedImage.getWidth(), bufferedImage.getHeight(), stage, scene, title);

        stage.show();
    }

    /**
     * Show multiple images in one window.
     *
     * @param title   Window title.
     * @param keepOpen If it is set to true, window can be close only manually. Close All button wont work on it.
     * @param showNames If it is set to true, image names will be shown in top left corner.
     * @param images Pair of buffered image and image name. Create pair with new Pair<>(bufferedImage, imageName).
     */
    public static void showMultipleImageInWindow(String title, boolean keepOpen, boolean showNames, Pair<BufferedImage, String>... images) {
        Stage stage = new Stage();
        stage.getIcons().add(FileBindings.favicon);
        HBox hBox = new HBox();
        BorderPane root = new BorderPane();
        root.setCenter(hBox);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setUserData(keepOpen);

        stage.setOnCloseRequest((e) -> openStages.remove(stage));
        openStages.add(stage);

        int jointWidth = 0;
        int maxHeight = 0;

        // Count max images width and height
        for (Pair<BufferedImage, String> image : images) {
            jointWidth += image.getKey().getWidth();
            maxHeight = Math.max(maxHeight, image.getKey().getHeight());
        }

        ImageView firstImageView = null;
        int imageWidth = 0;
        int imageHeight = 0;

        for (Pair<BufferedImage, String> imagePair : images) {
            BufferedImage image = imagePair.getKey();
            StackPane stackPane = setImageStackPane(image, imagePair.getValue(), scene, showNames, jointWidth, maxHeight);

            if (firstImageView == null) {
                firstImageView = (ImageView) stackPane.getChildren().get(0);
                imageWidth = image.getWidth();
                imageHeight = image.getHeight();
            }

            hBox.getChildren().add(stackPane);
        }

        handleMaxSize(stage, jointWidth, maxHeight);

        // Count image percentage of window size
        setUpSizeListener(firstImageView, imageWidth, imageHeight, stage, scene, title);

        stage.show();
    }

    /**
     * Close all open windows with images.
     */
    public static void closeAllWindows() {
        for (Stage stage : openStages) {
            stage.close();
        }
        openStages.clear();
    }

    /**
     * Close all images windows except windows with keepOpen set to true.
     */
    public static void closeImageWindows() {
        Iterator<Stage> iterator = openStages.iterator();
        while (iterator.hasNext()) {
            Stage stage = iterator.next();
            if (!(boolean) stage.getUserData()) {
                iterator.remove();
                stage.close();
            }
        }
    }

    /**
     * Load image from file into BufferedImage.
     *
     * @param imageFile File with image.
     * @return BufferedImage
     */
    public static BufferedImage loadImageFromPath(File imageFile) {
        if (!imageFile.exists()) throw new RuntimeException("Image file does not exist.");

        try {
            return ImageIO.read(imageFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Load image from path into BufferedImage.
     *
     * @param pathToIImage Path to image file.
     * @return BufferedImage
     */
    public static BufferedImage loadImageFromPath(String pathToIImage) {
        return loadImageFromPath(new File(pathToIImage));
    }

    /**
     * Open file chooser dialog and return selected file.
     * It is limited to image files only.
     * Default directory is Images directory in project root.
     *
     * @return Selected image file.
     */
    public static File openFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Change default image");
        fileChooser.setInitialDirectory(new File("Images/"));

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.jpg", "*.png", "*.bmp", "*.jpeg"));

        File ff = fileChooser.showOpenDialog(null);

        if (ff != null) {
            return ff;
        }

        System.out.println("Wrong file");
        return null;
    }

    private static Image convertImage(BufferedImage image) {
        // Convert buffered image to JavaFX image. Equivalent to SwingFXUtils.toFXImage(bufferedImage, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", baos);
            baos.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new Image(new ByteArrayInputStream(baos.toByteArray()));
    }

    private static StackPane setImageStackPane(BufferedImage image, String title, Scene scene, boolean showNames, int maxWidth, int maxHeight) {
        StackPane stackPane = new StackPane();

        Image fxImage = convertImage(image);

        ImageView imageView = new ImageView(fxImage);

        // Handle image resize
        imageView.setPreserveRatio(true);
        imageView.fitWidthProperty().bind(Bindings.multiply(scene.widthProperty(), fxImage.getWidth() / maxWidth));
        imageView.fitHeightProperty().bind(Bindings.multiply(scene.heightProperty(), fxImage.getHeight() / maxHeight));

        // Add image label
        stackPane.getChildren().add(imageView);

        if (showNames) {
            Label l = new Label(String.format("%dx%d %s", image.getWidth(), image.getHeight(), title));
            l.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5); -fx-text-fill: white;");
            stackPane.getChildren().add(l);
            StackPane.setAlignment(l, Pos.TOP_LEFT);
        }

        return stackPane;
    }

    private static void handleMaxSize(Stage stage, int maxWidth, int maxHeight) {
        // Handle bigger images
        double screenWidth = Toolkit.getDefaultToolkit().getScreenSize().getWidth();
        double screenHeight = Toolkit.getDefaultToolkit().getScreenSize().getHeight();

        if (maxWidth > screenWidth) {
            stage.setWidth(screenWidth);
        }

        if (maxHeight > screenHeight) {
            stage.setHeight(screenHeight);
        }
    }

    private static void setUpSizeListener(ImageView imageView, int imageWidth, int imageHeight, Stage stage, Scene scene, String title) {
        // Count image percentage of window size
        ChangeListener<Number> sizeListener = (observable, oldValue, newValue) -> {
            double scaleWidth = imageView.getFitWidth() / imageWidth;
            double scaleHeight = imageView.getFitHeight() / imageHeight;
            double percentageScale = Math.min(scaleWidth, scaleHeight) * 100;
            stage.setTitle(String.format("%s (%.2f%%)", title, percentageScale));
        };

        scene.widthProperty().addListener(sizeListener);
        scene.heightProperty().addListener(sizeListener);
    }
}
