package com.imgfloat.app.service.media;

import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

@Service
public class MediaPreviewService {
    private static final Logger logger = LoggerFactory.getLogger(MediaPreviewService.class);

    public byte[] encodePreview(BufferedImage image) {
        if (image == null) {
            return null;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            logger.warn("Unable to encode preview image", e);
            return null;
        }
    }

    public byte[] extractVideoPreview(byte[] bytes, String mediaType) {
        try (var channel = new ByteBufferSeekableByteChannel(ByteBuffer.wrap(bytes), bytes.length)) {
            FrameGrab grab = FrameGrab.createFrameGrab(channel);
            Picture frame = grab.getNativeFrame();
            if (frame == null) {
                return null;
            }
            BufferedImage image = AWTUtil.toBufferedImage(frame);
            return encodePreview(image);
        } catch (IOException | JCodecException e) {
            logger.warn("Unable to capture video preview frame for {}", mediaType, e);
            return null;
        }
    }
}
