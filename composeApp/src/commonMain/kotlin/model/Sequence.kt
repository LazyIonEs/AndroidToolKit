package model

/**
 * 列表排序
 */
enum class Sequence {
    DATE_NEW_TO_OLD, // 日期从新到旧
    DATE_OLD_TO_NEW, // 日期从旧到新
    SIZE_LARGE_TO_SMALL, // 大小从大到小
    SIZE_SMALL_TO_LARGE, // 大小从小到大
    NAME_A_TO_Z, // 名称从A到Z
    NAME_Z_TO_A, // 名称从Z到A
}