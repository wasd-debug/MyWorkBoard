export const commonTaskColors = [
  { name: "无颜色", value: "" },
  { name: "红色", value: "#ef5350" },
  { name: "橙色", value: "#f28c38" },
  { name: "黄色", value: "#e6b933" },
  { name: "绿色", value: "#48a868" },
  { name: "青色", value: "#35a6a0" },
  { name: "蓝色", value: "#4d83d1" },
  { name: "紫色", value: "#8b63c7" },
  { name: "粉色", value: "#d65b91" },
];

export const taskColorFamilies = [
  { name: "红", shades: ["#fff1f0", "#ffd6d2", "#ffaaa3", "#ff7b72", "#f05249", "#d9363e", "#ad1f2d"] },
  { name: "橙", shades: ["#fff4e8", "#ffe0bf", "#ffc58a", "#ffa64d", "#f28c38", "#d66a1f", "#a84c12"] },
  { name: "黄", shades: ["#fffbe6", "#fff1b8", "#ffe58f", "#ffd666", "#f2c94c", "#d4a928", "#a67c13"] },
  { name: "绿", shades: ["#f0f9eb", "#d8efc8", "#b5df9e", "#83c96c", "#4ea85b", "#31834a", "#21643d"] },
  { name: "青", shades: ["#e6fffb", "#b5f5ec", "#87e8de", "#5cdbd3", "#36bfb6", "#159c91", "#08736b"] },
  { name: "蓝", shades: ["#eaf4ff", "#c5ddff", "#96c0ff", "#6b9df0", "#4d83d1", "#3164ae", "#214782"] },
  { name: "紫", shades: ["#f5f0ff", "#e3d5ff", "#c6a9ff", "#a67bf0", "#855bd1", "#6840ad", "#4b2d82"] },
  { name: "粉", shades: ["#fff0f6", "#ffd6e7", "#ffadd2", "#f783ac", "#d65b91", "#b83a73", "#8f2859"] },
  { name: "灰", shades: ["#f5f5f5", "#e8e8e8", "#d1d1d1", "#ababab", "#858585", "#616161", "#414141"] },
];

export function isHexColor(value) {
  return /^#[\da-f]{6}$/i.test(value || "");
}
