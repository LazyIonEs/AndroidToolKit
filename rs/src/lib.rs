use std::fs::File;
use std::io::Write;
use std::vec::Vec;

use fast_image_resize::{IntoImageView, PixelType, Resizer};
use fast_image_resize::images::Image;
use image::{ColorType, DynamicImage, ExtendedColorType, GenericImageView, ImageEncoder};
use image::codecs::png::PngEncoder;
use image::io::Reader as ImageReader;
use mozjpeg::Marker;
use resize::{Pixel, Type};
use rgb::FromSlice;

use srgb::linear_to_srgb;

mod srgb;
include!("./lut.inc");

pub fn resize(input_path: String, output_path: String, dst_width: u32, dst_height: u32) -> Result<(), ToolKitRustError> {
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

    let (src_width, src_height) = src_image.dimensions();

    if src_width == dst_width && src_height == dst_height {
        let save_result = src_image.save(output_path);
        if save_result.is_err() {
            let err = format!("Failed to save: {}", save_result.unwrap_err());
            return Err(ToolKitRustError::Error(err));
        }
        return Ok(());
    }

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

// If `with_space_conversion` is true, this function returns 2 functions that
// convert from sRGB to linear RGB and vice versa. If `with_space_conversion` is
// false, the 2 functions returned do nothing.
fn srgb_converter_funcs(with_space_conversion: bool) -> (fn(u8) -> f32, fn(f32) -> u8) {
    if with_space_conversion {
        (
            |v| SRGB_TO_LINEAR_LUT[v as usize],
            |v| (linear_to_srgb(v) * 255.0).clamp(0.0, 255.0) as u8,
        )
    } else {
        (
            |v| (v as f32) / 255.0,
            |v| (v * 255.0).clamp(0.0, 255.0) as u8,
        )
    }
}

// If `with_alpha_premultiplication` is true, this function returns a function
// that premultiply the alpha channel with the given channel value and another
// function that reverses that process. If `with_alpha_premultiplication` is
// false, the functions just return the channel value.
fn alpha_multiplier_funcs(
    with_alpha_premultiplication: bool,
) -> (fn(f32, f32) -> f32, fn(f32, f32) -> f32) {
    if with_alpha_premultiplication {
        (|v, a| v * a, |v, a| v / a)
    } else {
        (|v, _a| v, |v, _a| v)
    }
}

pub fn resize_png(input_path: String, output_path: String, dst_width: u32, dst_height: u32, typ_idx: u8) -> Result<(), ToolKitRustError> {

    let src_image_open = image::open(input_path);

    if src_image_open.is_err() {
        let err = format!("Unable to open image: {}", src_image_open.err().unwrap());
        return Err(ToolKitRustError::Error(err));
    }

    let image = src_image_open.unwrap();

    let img = if is_rgba8(image.color()) {
        image
    } else {
        DynamicImage::from(image.to_rgba8())
    };

    let typ = match typ_idx {
        0 => Type::Triangle,
        1 => Type::Catrom,
        2 => Type::Mitchell,
        3 => Type::Lanczos3,
        _ => panic!("Nope"),
    };

    let (width, height) = img.dimensions();
    let num_input_pixels = (width * height) as usize;
    let num_output_pixels = (dst_width * dst_height) as usize;

    let input_image = img.into_bytes();
    let mut output_image = vec![0u8; num_output_pixels * 4];
    let len = input_image.len();

    // Otherwise, we convert to f32 images to keep the
    // conversions as lossless and high-fidelity as possible.
    let (to_linear, to_srgb) = srgb_converter_funcs(true);
    let (premultiplier, demultiplier) = alpha_multiplier_funcs(true);

    let mut preprocessed_input_image: Vec<f32> = Vec::with_capacity(len);
    preprocessed_input_image.resize(len, 0.0f32);
    for i in 0..num_input_pixels {
        for j in 0..3 {
            preprocessed_input_image[4 * i + j] = premultiplier(
                to_linear(input_image[4 * i + j]),
                (input_image[4 * i + 3] as f32) / 255.0,
            );
        }
        preprocessed_input_image[4 * i + 3] = (input_image[4 * i + 3] as f32) / 255.0;
    }

    let mut unprocessed_output_image = vec![0.0f32; num_output_pixels * 4];

    let resizer_result = resize::new(
        width as usize,
        height as usize,
        dst_width as usize,
        dst_height as usize,
        Pixel::RGBAF32,
        typ,
    );

    if resizer_result.is_err() {
        let err = format!("Fail to create a new resizer instance: {}", resizer_result.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }

    let mut resizer = resizer_result.unwrap();

    let resize_result = resizer.resize(
        preprocessed_input_image.as_rgba(),
        unprocessed_output_image.as_rgba_mut(),
    );

    if resize_result.is_err() {
        let err = format!("Fail to resize src image data into dst.: {}", resize_result.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }

    resize_result.unwrap();

    for i in 0..num_output_pixels {
        for j in 0..3 {
            output_image[4 * i + j] = to_srgb(demultiplier(
                unprocessed_output_image[4 * i + j],
                unprocessed_output_image[4 * i + 3],
            ));
        }
        output_image[4 * i + 3] = (unprocessed_output_image[4 * i + 3] * 255.0)
            .round()
            .clamp(0.0, 255.0) as u8;
    }

    let output_file = File::create(output_path);
    if output_file.is_err() {
        let err = format!("Failed to create file: {}", output_file.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }

    let jpeg = PngEncoder::new(output_file.unwrap());
    let write_result = jpeg.write_image(&*output_image, dst_width, dst_height, ExtendedColorType::Rgba8);

    if write_result.is_err() {
        let err = format!("Failed to write file: {}", write_result.unwrap_err());
        return Err(ToolKitRustError::Error(err));
    }

    Ok(())
}

fn is_rgba8(color: ColorType) -> bool {
    match color {
        ColorType::L8 => false,
        ColorType::La8 => false,
        ColorType::Rgb8 => false,
        ColorType::Rgba8 => true,
        ColorType::L16 => false,
        ColorType::La16 => false,
        ColorType::Rgb16 => false,
        ColorType::Rgba16 => false,
        ColorType::Rgb32F => false,
        ColorType::Rgba32F => false,
        _ => false
    }
}

pub fn quantize(input_path: String, output_path: String) -> Result<(), ToolKitRustError> {
    // 解码图片
    let img = lodepng::decode32_file(input_path).unwrap();
    let (width, height) = (img.width, img.height);

    // 准备量化图片
    let mut attributes = imagequant::Attributes::new();
    attributes.set_speed(1).unwrap();
    attributes.set_quality(85, 100).unwrap();
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
    compress.set_quality(85.0);
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
