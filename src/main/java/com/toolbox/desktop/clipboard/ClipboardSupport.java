package com.toolbox.desktop.clipboard;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClipboardSupport {

    private ClipboardSupport() {
    }

    public static BufferedImage toBufferedImage(Image image) {
        if (image instanceof BufferedImage) {
            BufferedImage source = (BufferedImage) image;
            BufferedImage copy = new BufferedImage(
                    source.getWidth(),
                    source.getHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D g = copy.createGraphics();
            try {
                g.drawImage(source, 0, 0, null);
            } finally {
                g.dispose();
            }
            return copy;
        }

        int width = image.getWidth(null);
        int height = image.getHeight(null);
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image has invalid size");
        }
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buffered.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return buffered;
    }

    public static String hashText(String text) {
        return sha256(text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));
    }

    public static String hashFiles(List<File> files) {
        StringBuilder builder = new StringBuilder();
        for (File file : files) {
            builder.append(file.getAbsolutePath()).append('\n');
        }
        return hashText(builder.toString());
    }

    public static String hashImage(BufferedImage image) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", output);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot encode image for hashing", e);
        }
        return sha256(output.toByteArray());
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(bytes);
            StringBuilder result = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                result.append(String.format("%02x", b & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static boolean restore(ClipboardEntry entry, ClipboardHistoryStore store) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        try {
            if (entry.getType() == ClipboardEntry.Type.TEXT) {
                clipboard.setContents(new java.awt.datatransfer.StringSelection(entry.getText()), null);
                return true;
            }
            if (entry.getType() == ClipboardEntry.Type.IMAGE) {
                BufferedImage image = ImageIO.read(store.resolveImage(entry));
                if (image == null) {
                    return false;
                }
                clipboard.setContents(new ImageTransferable(image), null);
                return true;
            }
            if (entry.getType() == ClipboardEntry.Type.FILES) {
                List<File> files = new ArrayList<File>();
                for (String path : entry.getFilePaths()) {
                    files.add(new File(path));
                }
                clipboard.setContents(new FileListTransferable(files), null);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static final class ImageTransferable implements Transferable {
        private final Image image;

        public ImageTransferable(Image image) {
            this.image = image;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }

    public static final class FileListTransferable implements Transferable {
        private final List<File> files;

        public FileListTransferable(List<File> files) {
            this.files = Collections.unmodifiableList(new ArrayList<File>(files));
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.javaFileListFlavor};
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.javaFileListFlavor.equals(flavor);
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return files;
        }
    }
}
