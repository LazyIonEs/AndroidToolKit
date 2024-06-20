use std::fs::File;
use std::io::Write;
use std::vec::Vec;

use fast_image_resize::{IntoImageView, PixelType, Resizer};
use fast_image_resize::images::Image;
use image::ColorType;
use image::io::Reader as ImageReader;
use mozjpeg::Marker;

pub fn resize(input_path: String, output_path: String, dst_scale: f32) -> Result<(), ToolKitRustError> {
    let src_image_open = ImageReader::open(input_path);

    if src_image_open.is_err() {
        let err = format!("Unable to open image: {}", src_image_open.err().unwrap());
        return Err(ToolKitRustError::Error(err));
    }

    let src_image_decode = src_image_open.unwrap().decode();

    if src_image_decode.is_err() {
        let err = format!("Unable to decode image: {}", src_image_decode.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }

    let src_image = src_image_decode.unwrap();

    let dst_width = ((src_image.width() as f32) * dst_scale).round() as u32;
    let dst_height = ((src_image.height() as f32) * dst_scale).round() as u32;

    let mut dst_image = Image::new(
        dst_width,
        dst_height,
        src_image.pixel_type().unwrap(),
    );

    let mut resizer = Resizer::new();
    if let Err(e) = resizer.resize(&src_image, &mut dst_image, None) {
        let err = format!("Failed to resize: {e}");
        return Err(ToolKitRustError::Error(err));
    }

    let color_type = match dst_image.pixel_type() {
        PixelType::U8 => ColorType::L8,
        PixelType::U8x2 => ColorType::La8,
        PixelType::U8x3 => ColorType::Rgb8,
        PixelType::U8x4 => ColorType::Rgba8,
        PixelType::U16 => ColorType::L16,
        PixelType::U16x2 => ColorType::La16,
        PixelType::U16x3 => ColorType::Rgb16,
        PixelType::U16x4 => ColorType::Rgba16,
        _ => return Err(ToolKitRustError::Error("Unsupported type of pixels".to_string())),
    };

    if let Err(e) = image::save_buffer(
        output_path,
        dst_image.buffer(),
        dst_image.width(),
        dst_image.height(),
        color_type,
    ) {
        let err = format!("Failed to save buffer: {e}");
        return Err(ToolKitRustError::Error(err));
    }

    Ok(())
}

pub fn quantize(input_path: String, output_path: String) -> Result<(), ToolKitRustError> {
    // 解码图片
    let img = lodepng::decode32_file(input_path).unwrap();
    let (width, height) = (img.width, img.height);

    // 准备量化图片
    let mut attributes = imagequant::Attributes::new();
    attributes.set_speed(1).unwrap();
    attributes.set_quality(75, 80).unwrap();
    let created_image = attributes.new_image(&*img.buffer, width, height, 0.0);
    if created_image.is_err() {
        let err = format!("Unable to create image: {}", created_image.err().unwrap());
        return Err(ToolKitRustError::Error(err));
    }
    let mut image = created_image.unwrap();
    let quantization = attributes.quantize(&mut image);
    if quantization.is_err() {
        let err = format!("Fail to quantize: {}", quantization.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }
    let mut quantization_result = quantization.unwrap();
    quantization_result.set_dithering_level(1.0).unwrap();
    let remapped = quantization_result.remapped(&mut image);
    if remapped.is_err() {
        let err = format!("Failed to remap image into a palette + indices: {}", remapped.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }
    let (palette, pixels) = remapped.unwrap();
    let mut encoder = lodepng::Encoder::new();
    let encoder_palette = encoder.set_palette(&palette);
    if encoder_palette.is_err() {
        let err = format!("Failed to encoder set palette: {}", encoder_palette.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }

    // 量化后编码图片得到vec
    let png_vec = encoder.encode(&pixels, width, height);
    if png_vec.is_err() {
        let err = format!("Failed to encode file: {}", png_vec.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }

    // 使用oxipng再次进行无损压缩
    let mut options = oxipng::Options::from_preset(6);
    options.optimize_alpha = true;
    options.interlace = Some(oxipng::Interlacing::None);
    let oxipng_vec = oxipng::optimize_from_memory(&png_vec.unwrap(), &options);
    if oxipng_vec.is_err() {
        let err = format!("Failed to optimize from memory: {}", oxipng_vec.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }

    // 将vec写入文件
    let output_file = File::create(output_path);
    if output_file.is_err() {
        let err = format!("Failed to create file: {}", output_file.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }
    let write_result = output_file.unwrap().write_all(&oxipng_vec.unwrap());
    if write_result.is_err() {
        let err = format!("Failed to write file: {}", write_result.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }
    Ok(())
}

pub const ALL_MARKERS: &[Marker] = &[
    Marker::APP(0), Marker::APP(1), Marker::APP(2), Marker::APP(3), Marker::APP(4),
    Marker::APP(5), Marker::APP(6), Marker::APP(7), Marker::APP(8), Marker::APP(9),
    Marker::APP(10), Marker::APP(11), Marker::APP(12), Marker::APP(13), Marker::APP(14),
    Marker::COM,
];

pub fn mozjpeg(input_path: String, output_path: String) -> Result<(), ToolKitRustError> {
    let decompress_builder = mozjpeg::decompress::DecompressBuilder::new();
    let image = decompress_builder.with_markers(ALL_MARKERS)
        .from_path(input_path).unwrap();

    let rgb_image_result = image.rgb();
    if rgb_image_result.is_err() {
        let err = format!("Failed to convert image to RGB: {}", rgb_image_result.err().unwrap());
        return Err(ToolKitRustError::Error(err));
    }
    let mut rgb_image = rgb_image_result.unwrap();
    let pixels_vec = rgb_image.read_scanlines();
    if pixels_vec.is_err() {
        let err = format!("Failed to read scanline: {}", pixels_vec.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }
    let (width, height) = (rgb_image.width(), rgb_image.height());
    let color_space = rgb_image.color_space();
    let finish_result = rgb_image.finish();
    if finish_result.is_err() {
        let err = format!("Failed to finish decompress: {}", finish_result.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }

    let mut compress = mozjpeg::Compress::new(color_space);
    compress.set_size(width, height);
    compress.set_quality(75.0);
    let comp = compress.start_compress(Vec::new());
    if comp.is_err() {
        let err = format!("Failed to start compress: {}", comp.err().unwrap());
        return Err(ToolKitRustError::Error(err));
    }

    let mut comp_vec = comp.unwrap();

    let scanline_result = comp_vec.write_scanlines(&pixels_vec.unwrap());
    if scanline_result.is_err() {
        let err = format!("Failed to write scanline: {}", scanline_result.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }
    let writer = comp_vec.finish();

    if writer.is_err() {
        let err = format!("Failed to finish decompress: {}", writer.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }

    // 将vec写入文件
    let output_file = File::create(output_path);
    if output_file.is_err() {
        let err = format!("Failed to create file: {}", output_file.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }
    let write_result = output_file.unwrap().write_all(writer.unwrap().as_mut_slice());
    if write_result.is_err() {
        let err = format!("Failed to write file: {}", write_result.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }

    Ok(())
}

#[derive(Debug, thiserror::Error)]
pub enum ToolKitRustError {
    #[error("{0}")]
    Error(String)
}

uniffi::include_scaffolding!("toolkit");
