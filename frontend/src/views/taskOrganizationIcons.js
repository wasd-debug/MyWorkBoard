import {
  AlarmClock,
  AlarmClockCheck,
  Archive,
  Award,
  Baby,
  BadgeCheck,
  Bike,
  BookMarked,
  BookOpen,
  Briefcase,
  Building2,
  Bug,
  Calculator,
  CalendarCheck,
  CalendarClock,
  CalendarDays,
  Camera,
  CarFront,
  ChartNoAxesCombined,
  CheckCircle2,
  CheckSquare,
  CircleCheck,
  CircleDollarSign,
  ClipboardList,
  Clock3,
  Cloud,
  CloudSun,
  Code2,
  Coffee,
  Compass,
  Cpu,
  Database,
  Dumbbell,
  Earth,
  FileText,
  Film,
  Flag,
  Flower2,
  Folder,
  FolderKanban,
  Gamepad2,
  Gift,
  Globe,
  GraduationCap,
  Handshake,
  Headphones,
  HeartPulse,
  House,
  Image,
  Languages,
  Laptop,
  Leaf,
  LibraryBig,
  Lightbulb,
  ListChecks,
  ListTodo,
  LockKeyhole,
  Map,
  MapPin,
  Monitor,
  Moon,
  Mountain,
  Music2,
  NotebookPen,
  NotebookTabs,
  Palette,
  PawPrint,
  Pencil,
  PiggyBank,
  Pill,
  Plane,
  Presentation,
  Rocket,
  Server,
  Settings2,
  Shirt,
  ShoppingCart,
  Smartphone,
  Smile,
  Sparkles,
  Star,
  Sun,
  Tag,
  Tags,
  Target,
  Terminal,
  Timer,
  TrainFront,
  TreePine,
  Trophy,
  Utensils,
  UsersRound,
  Wallet,
  Wrench,
  Zap,
} from "lucide-vue-next";

const categoryDefinitions = [
  { value: "all", label: "全部" },
  { value: "task", label: "任务效率" },
  { value: "work", label: "工作项目" },
  { value: "study", label: "学习阅读" },
  { value: "life", label: "生活健康" },
  { value: "tech", label: "科技工具" },
  { value: "creative", label: "创意娱乐" },
  { value: "travel", label: "出行自然" },
  { value: "symbol", label: "符号标记" },
  { value: "classic", label: "经典表情" },
];

const lucideEntries = [
  ["ListTodo", ListTodo, "任务清单", "待办 任务 列表"], ["ListChecks", ListChecks, "检查清单", "待办 完成"], ["CheckSquare", CheckSquare, "方框勾选", "完成 待办"], ["CircleCheck", CircleCheck, "圆形完成", "完成"], ["CheckCircle2", CheckCircle2, "完成事项", "完成"], ["CalendarDays", CalendarDays, "日历", "日期 计划"], ["CalendarClock", CalendarClock, "日程时间", "日期 时间"], ["CalendarCheck", CalendarCheck, "日程完成", "日期 完成"], ["AlarmClock", AlarmClock, "闹钟", "提醒 时间"], ["AlarmClockCheck", AlarmClockCheck, "提醒完成", "提醒 时间"], ["Clock3", Clock3, "时钟", "时间"], ["Timer", Timer, "计时器", "专注 番茄钟"], ["BadgeCheck", BadgeCheck, "认证完成", "完成 徽章"], ["Archive", Archive, "归档", "收纳"],
  ["Briefcase", Briefcase, "公文包", "工作 职场"], ["Building2", Building2, "办公楼", "公司 工作"], ["Presentation", Presentation, "演示", "汇报 演讲"], ["UsersRound", UsersRound, "团队", "成员 协作"], ["Handshake", Handshake, "协作", "合作 团队"], ["FolderKanban", FolderKanban, "项目看板", "项目 管理"], ["ChartNoAxesCombined", ChartNoAxesCombined, "业绩图表", "数据 统计"], ["FileText", FileText, "文档", "文件 资料"], ["Folder", Folder, "文件夹", "目录 分组"], ["ClipboardList", ClipboardList, "工作记录", "清单 计划"],
  ["BookOpen", BookOpen, "打开的书", "阅读 书籍"], ["BookMarked", BookMarked, "书签书籍", "阅读 收藏"], ["LibraryBig", LibraryBig, "图书馆", "阅读 书籍"], ["GraduationCap", GraduationCap, "毕业帽", "学校 学习"], ["NotebookPen", NotebookPen, "笔记本", "笔记 记录"], ["NotebookTabs", NotebookTabs, "活页笔记", "笔记 资料"], ["Languages", Languages, "语言", "翻译 外语"], ["Calculator", Calculator, "计算器", "数学"], ["Lightbulb", Lightbulb, "灯泡", "想法 灵感"], ["Pencil", Pencil, "铅笔", "编辑 写作"],
  ["House", House, "房屋", "家庭 居家"], ["Coffee", Coffee, "咖啡", "饮品 休息"], ["Utensils", Utensils, "餐具", "吃饭 餐饮"], ["ShoppingCart", ShoppingCart, "购物车", "采购 买东西"], ["CarFront", CarFront, "汽车", "交通 出行"], ["Bike", Bike, "自行车", "运动 出行"], ["HeartPulse", HeartPulse, "健康心跳", "医疗 运动"], ["Pill", Pill, "药片", "健康 医疗"], ["Dumbbell", Dumbbell, "哑铃", "健身 运动"], ["PawPrint", PawPrint, "爪印", "宠物"], ["Baby", Baby, "婴儿", "家庭 育儿"], ["Shirt", Shirt, "衣服", "穿搭 购物"], ["Smile", Smile, "笑脸", "心情"],
  ["Laptop", Laptop, "笔记本电脑", "科技 设备"], ["Smartphone", Smartphone, "手机", "设备 通讯"], ["Monitor", Monitor, "显示器", "电脑 设备"], ["Code2", Code2, "代码", "开发 编程"], ["Terminal", Terminal, "终端", "开发 命令"], ["Database", Database, "数据库", "数据"], ["Cloud", Cloud, "云端", "网络 存储"], ["Server", Server, "服务器", "技术 运维"], ["Cpu", Cpu, "芯片", "电脑 硬件"], ["Wrench", Wrench, "扳手", "维修 工具"], ["Settings2", Settings2, "设置", "工具 配置"], ["Bug", Bug, "缺陷", "开发 问题"], ["LockKeyhole", LockKeyhole, "安全锁", "安全 密码"],
  ["Palette", Palette, "调色盘", "创意 设计"], ["Camera", Camera, "相机", "摄影 图片"], ["Image", Image, "图片", "摄影 设计"], ["Music2", Music2, "音乐", "歌曲 音频"], ["Headphones", Headphones, "耳机", "音乐 音频"], ["Film", Film, "电影", "视频 娱乐"], ["Gamepad2", Gamepad2, "游戏手柄", "游戏 娱乐"], ["Sparkles", Sparkles, "闪光", "灵感 创意"],
  ["Plane", Plane, "飞机", "旅行 出差"], ["TrainFront", TrainFront, "火车", "旅行 交通"], ["Map", Map, "地图", "旅行 导航"], ["MapPin", MapPin, "地点标记", "地址 旅行"], ["Compass", Compass, "指南针", "方向 户外"], ["Mountain", Mountain, "山脉", "户外 自然"], ["TreePine", TreePine, "松树", "自然 户外"], ["Flower2", Flower2, "花朵", "自然 植物"], ["Leaf", Leaf, "叶子", "植物 自然"], ["Sun", Sun, "太阳", "天气"], ["Moon", Moon, "月亮", "夜晚"], ["CloudSun", CloudSun, "多云转晴", "天气"], ["Earth", Earth, "地球", "世界 自然"], ["Globe", Globe, "全球", "世界 网络"],
  ["Star", Star, "星星", "收藏 重要"], ["Award", Award, "奖章", "成就"], ["Trophy", Trophy, "奖杯", "目标 成就"], ["Target", Target, "目标", "计划 重点"], ["Flag", Flag, "旗帜", "标记 重要"], ["Tag", Tag, "标签", "分类"], ["Tags", Tags, "多个标签", "分类"], ["CircleDollarSign", CircleDollarSign, "金额", "财务 预算"], ["Wallet", Wallet, "钱包", "财务"], ["PiggyBank", PiggyBank, "储蓄罐", "存钱 财务"], ["Gift", Gift, "礼物", "生日 节日"], ["Rocket", Rocket, "火箭", "启动 目标"], ["Zap", Zap, "闪电", "快速 能量"],
].map(([value, component, label, keywords], index) => ({
  value,
  component,
  label,
  keywords,
  category: categoryDefinitions[
    index < 14 ? 1 : index < 24 ? 2 : index < 34 ? 3 : index < 47 ? 4 : index < 60 ? 5 : index < 68 ? 6 : index < 82 ? 7 : 8
  ].value,
}));

const classicEntries = [
  ["📋", "清单"], ["📝", "笔记"], ["📚", "书籍"], ["💼", "工作"], ["🧠", "思考"], ["🛒", "购物"], ["⭐", "星标"], ["📁", "文件夹"],
  ["🏠", "家庭"], ["🏷️", "标签"], ["🔎", "搜索"], ["📌", "固定"], ["🎯", "目标"], ["💡", "灵感"], ["🎨", "设计"], ["💻", "电脑"],
  ["📅", "日历"], ["✈️", "旅行"], ["🚗", "汽车"], ["🏃", "运动"], ["🍽️", "餐饮"], ["☕", "咖啡"], ["🎓", "学习"], ["💰", "财务"],
  ["❤️", "健康"], ["🎵", "音乐"], ["📷", "照片"], ["🔧", "工具"], ["🌱", "成长"], ["🎁", "礼物"], ["🏆", "成就"], ["🌍", "世界"],
  ["👥", "团队"], ["🐾", "宠物"], ["🧳", "行李"], ["🧹", "家务"],
].map(([value, label]) => ({ value, component: null, label, keywords: "emoji 表情 经典", category: "classic" }));

export const organizationIconCategories = categoryDefinitions;
export const organizationIconCatalog = [...lucideEntries, ...classicEntries];
export const organizationIconComponents = Object.fromEntries(lucideEntries.map(({ value, component }) => [value, component]).filter(([, component]) => component));

export function resolveOrganizationIcon(value) {
  return organizationIconComponents[value] || null;
}

export function searchOrganizationIcons(query = "", category = "all") {
  const normalized = query.trim().toLocaleLowerCase();
  return organizationIconCatalog.filter((item) => {
    const matchesCategory = category === "all" || item.category === category;
    const text = `${item.value} ${item.label} ${item.keywords}`.toLocaleLowerCase();
    return matchesCategory && (!normalized || text.includes(normalized));
  });
}
