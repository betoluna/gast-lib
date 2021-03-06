/*
 * Copyright 2011 Jon A. Webb
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *              http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package root.gast.playground.image.detectlogo;

import root.gast.image.ImageCameraView;
import root.gast.image.LogoView;
import root.gast.image.ViewLogo;
import jjil.algorithm.Gray8ConnComp;
import jjil.algorithm.Gray8Flip;
import jjil.algorithm.Gray8Reduce;
import jjil.algorithm.Gray8Rgb;
import jjil.algorithm.Gray8Rotate90;
import jjil.algorithm.Gray8Threshold;
import jjil.algorithm.RgbAbsDiffGray;
import jjil.algorithm.Gray8Flip.Axis;
import jjil.android.DebugImage;
import jjil.android.Nv212RgbImage;
import jjil.android.RgbImageAndroid;
import jjil.core.Error;
import jjil.core.Image;
import jjil.core.PipelineStage;
import jjil.core.Rect;
import jjil.core.RgbImage;
import jjil.core.RgbVal;
import jjil.core.Sequence;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

public class DetectLogo implements android.hardware.Camera.PreviewCallback
{
    private boolean mbMirror;
    private Gray8ConnComp mG8cc;
    private PipelineStage mDisplay;
    private ImageCameraView mImageView;
    private LogoView mLogoView;
    private Sequence mSeqThreshold;

    public DetectLogo(ImageCameraView view, LogoView lv)
    {
        mImageView = view;
        mLogoView = lv;
        mImageView.setScaleType(ScaleType.FIT_XY);
        // calculate the absolute value of the difference between the RGB value
        // and magenta. A small number here corresponds to a greenish pixel
        RgbAbsDiffGray radg = new RgbAbsDiffGray(Color.GREEN);
        // then pass all pixels less than -48 (96 values above min)
        Gray8Threshold g8t = new Gray8Threshold(-48, true);
        // Now build the pipeline
        mSeqThreshold = new Sequence(radg);
        mSeqThreshold.add(g8t);
        mG8cc = new Gray8ConnComp();
        mDisplay = new Gray8Rgb();
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera)
    {
        Parameters cameraParameters = camera.getParameters();
        int imageFormat = cameraParameters.getPreviewFormat();
        // we only know how to process NV21 format (the default format)
        if (imageFormat == ImageFormat.NV21)
        {
            Size size = camera.getParameters().getPreviewSize();
            /**
             * for debugging purposes, allow the current image to be saved and
             * reused
             */
            boolean bWrite = false, bRead = false;
            Integer width = size.width, height = size.height;
            if (bWrite)
            {
                DebugImage.writeNv21Image(data, width, height, "logo.png");
            }
            if (bRead)
            {
                DebugImage.Nv21Image nv21Image = DebugImage
                        .readImage2Nv21("logo.png");
                width = nv21Image.getWidth();
                height = nv21Image.getHeight();
                data = nv21Image.getData();
            }
            // first convert to an RGB image for processing
            RgbImage rgb = Nv212RgbImage.getRgbImage(data, width, height);
            // next push the rgb image into the pre-built pipeline
            try
            {
                mSeqThreshold.push(rgb);
                Image imThresholded = mSeqThreshold.getFront();
                // no need to make a copy here, neither PipelineStage
                // modifies its input
                mDisplay.push(imThresholded);
                // show the thresholded image
                RgbImage imDisplay = (RgbImage) mDisplay.getFront();
                mImageView.setImageBitmap(RgbImageAndroid.toBitmap(imDisplay));
                mImageView.invalidate();
                mG8cc.push(imThresholded);
                // calculate connected components. We make this a member
                // variable
                // so we can access the connected components
                // take the largest connected component
                if (mG8cc.getComponentCount() > 0)
                {
                    Rect r = mG8cc.getComponent(0);
                    // note: the image we displayed is rotated counter-clockwise
                    // compared
                    // to the image mG8cc processed. We must rotate r
                    // appropriately
                    RectF rf = new RectF(r.getLeft(), r.getTop(), r.getRight(), r.getBottom());
                    mLogoView.setRect(rf);
                } else {
                    mLogoView.clearRect();
                }
            } catch (Error e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
