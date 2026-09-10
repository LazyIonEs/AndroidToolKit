#[allow(dead_code)]
pub trait Clamp: PartialOrd + Sized {
    fn clamp(self, min: Self, max: Self) -> Self {
        if self.lt(&min) {
            min
        } else if self.gt(&max) {
            max
        } else {
            self
        }
    }
}

impl Clamp for f32 {}

#[allow(dead_code)]
/// 将归一化 sRGB 通道值转换为线性 RGB，低亮度段使用线性分支。
pub fn srgb_to_linear(v: f32) -> f32 {
    if v < 0.04045 {
        v / 12.92
    } else {
        ((v + 0.055) / 1.055).powf(2.4).clamp(0.0, 1.0)
    }
}

/// 将线性 RGB 通道值转回 sRGB；输出到 8 位像素前由调用方再缩放和限制范围。
pub fn linear_to_srgb(v: f32) -> f32 {
    if v < 0.0031308 {
        v * 12.92
    } else {
        (1.055 * v.powf(1.0 / 2.4) - 0.055).clamp(0.0, 1.0)
    }
}
