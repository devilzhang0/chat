<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref} from 'vue';
import {Search, X} from 'lucide-vue-next';

const emit = defineEmits<{
  (e: 'select', emoji: string): void;
  (e: 'close'): void;
}>();

interface EmojiItem {
  char: string;
  keywords: string;
}

interface EmojiCategory {
  id: string;
  icon: string;
  label: string;
  emojis: EmojiItem[];
}

const RECENT_STORAGE_KEY = 'mychat_recent_emojis';
const MAX_RECENT = 24;

const CATEGORIES: EmojiCategory[] = [
  {
    id: 'faces',
    icon: '😀',
    label: '表情与情绪',
    emojis: [
      {char: '😀', keywords: '笑 开心 grin smile happy'},
      {char: '😃', keywords: '大笑 开心 smile happy'},
      {char: '😄', keywords: '开心 哈哈 laugh happy'},
      {char: '😁', keywords: '露齿笑 嘻嘻 grin'},
      {char: '😆', keywords: '大笑 哈哈 haha xd'},
      {char: '😅', keywords: '苦笑 流汗 尴尬 sweat'},
      {char: '🤣', keywords: '笑哭 爆笑 rofl lol'},
      {char: '😂', keywords: '笑哭 破涕为笑 joy lol'},
      {char: '🙂', keywords: '微笑 呵呵 slight smile'},
      {char: '🙃', keywords: '倒笑 微笑 无奈 upside'},
      {char: '🫠', keywords: '融化 社死 尴尬 melt'},
      {char: '😉', keywords: '眨眼 俏皮 wink'},
      {char: '😊', keywords: '害羞 微笑 开心 blush'},
      {char: '😇', keywords: '天使 光环 乖巧 angel'},
      {char: '🥰', keywords: '喜欢 爱 幸福 love hearts'},
      {char: '😍', keywords: '花痴 爱慕 喜欢 heart eyes'},
      {char: '🤩', keywords: '星星眼 崇拜 惊喜 star'},
      {char: '😘', keywords: '飞吻 亲亲 kiss love'},
      {char: '😗', keywords: '亲亲 嘟嘴 kiss'},
      {char: '😚', keywords: '害羞亲亲 kiss'},
      {char: '😋', keywords: '馋 好吃 美味 yum'},
      {char: '😛', keywords: '吐舌 调皮 tongue'},
      {char: '😜', keywords: '眨眼吐舌 调皮 wink tongue'},
      {char: '🤪', keywords: '滑稽 疯狂 搞笑 zany crazy'},
      {char: '😝', keywords: '鬼脸 调皮 squint tongue'},
      {char: '🤑', keywords: '发财 钱 财迷 money'},
      {char: '🤗', keywords: '抱抱 拥抱 hug'},
      {char: '🤭', keywords: '偷笑 捂嘴 giggle'},
      {char: '🫢', keywords: '惊讶 捂嘴 gasp'},
      {char: '🫣', keywords: '偷看 害羞 peek'},
      {char: '🤫', keywords: '嘘 安静 保密 shush'},
      {char: '🤔', keywords: '思考 疑惑 想 think'},
      {char: '🫡', keywords: '敬礼 收到 致敬 salute'},
      {char: '🤐', keywords: '闭嘴 沉默 zipper'},
      {char: '🤨', keywords: '挑眉 怀疑 eyebrow'},
      {char: '😐', keywords: '面无表情 冷静 neutral'},
      {char: '😑', keywords: '无语 漠然 expressionless'},
      {char: '😶', keywords: '沉默 无言 mute'},
      {char: '🫥', keywords: '隐身 社恐 透明 dotted'},
      {char: '😏', keywords: '坏笑 得意 smirk'},
      {char: '😒', keywords: '嫌弃 白眼 不爽 unamused'},
      {char: '🙄', keywords: '翻白眼 无语 eye roll'},
      {char: '😬', keywords: '龇牙 尴尬 grimace'},
      {char: '😮‍💨', keywords: '叹气 松一口气 sigh exhale'},
      {char: '🤥', keywords: '说谎 长鼻子 lie'},
      {char: '😌', keywords: '安心 释然 舒服 relieved'},
      {char: '😔', keywords: '失落 难过 pensive'},
      {char: '😪', keywords: '困 瞌睡 sleepy'},
      {char: '🤤', keywords: '流口水 馋 drool'},
      {char: '😴', keywords: '睡觉 晚安 sleep zzz'},
      {char: '😷', keywords: '口罩 生病 mask sick'},
      {char: '🤒', keywords: '发烧 生病 fever'},
      {char: '🤕', keywords: '受伤 绷带 hurt'},
      {char: '🤢', keywords: '恶心 想吐 nauseated'},
      {char: '🤮', keywords: '呕吐 吐 vomit'},
      {char: '🤧', keywords: '打喷嚏 感冒 sneeze'},
      {char: '🥵', keywords: '热 发烫 hot'},
      {char: '🥶', keywords: '冷 冻僵 cold'},
      {char: '🥴', keywords: '晕 醉 woozy'},
      {char: '😵‍💫', keywords: '头晕 眼花 dizzy'},
      {char: '🤯', keywords: '爆炸 震撼 裂开 mind blown'},
      {char: '🤠', keywords: '牛仔 酷 cowboy'},
      {char: '🥳', keywords: '派对 庆祝 生日 party'},
      {char: '🥸', keywords: '伪装 眼镜 disguise'},
      {char: '😎', keywords: '墨镜 酷 帅 cool sunglasses'},
      {char: '🤓', keywords: '学霸 眼镜 书呆子 nerd'},
      {char: '🧐', keywords: '观察 审视 侦探 monocle'},
      {char: '😕', keywords: '困惑 迷茫 confused'},
      {char: '😟', keywords: '担心 忧虑 worried'},
      {char: '😮', keywords: '惊讶 哇 surprised'},
      {char: '😲', keywords: '震惊 吃惊 astonished'},
      {char: '😳', keywords: '脸红 害羞 尴尬 flushed'},
      {char: '🥺', keywords: '委屈 拜托 泪目 pleading'},
      {char: '🥹', keywords: '感动 强忍泪水 holding tears'},
      {char: '😨', keywords: '害怕 惊恐 fearful'},
      {char: '😰', keywords: '焦虑 冷汗 anxious'},
      {char: '😢', keywords: '哭 难过 流泪 cry sad'},
      {char: '😭', keywords: '大哭 泪奔 破防 sob cry'},
      {char: '😱', keywords: '尖叫 吓死 scream shock'},
      {char: '😖', keywords: '纠结 痛苦 confounded'},
      {char: '😣', keywords: '忍耐 难受 persevere'},
      {char: '😞', keywords: '失望 沮丧 disappointed'},
      {char: '😓', keywords: '冷汗 辛苦 downcast sweat'},
      {char: '😩', keywords: '累 崩溃 weary tired'},
      {char: '🥱', keywords: '打哈欠 困 无聊 yawn'},
      {char: '😤', keywords: '生气 哼 傲娇 triumph'},
      {char: '😡', keywords: '愤怒 生气 怒 angry rage'},
      {char: '😠', keywords: '生气 不高兴 angry'},
      {char: '🤬', keywords: '暴怒 骂人 curse'},
      {char: '😈', keywords: '小恶魔 坏笑 devil'},
      {char: '💀', keywords: '骷髅 笑死 完蛋 skull dead'},
      {char: '💩', keywords: '便便 搞笑 poop'},
      {char: '🤡', keywords: '小丑 竟是我自己 clown'},
      {char: '👻', keywords: '幽灵 搞怪 ghost'},
      {char: '👽', keywords: '外星人 alien'},
      {char: '🤖', keywords: '机器人 ai robot'},
      {char: '😺', keywords: '猫 开心 cat smile'},
      {char: '😸', keywords: '猫 大笑 cat grin'},
      {char: '😹', keywords: '猫 笑哭 cat joy'},
      {char: '😻', keywords: '猫 爱心 cat love'},
      {char: '😿', keywords: '猫 哭 cat cry'},
      {char: '🙈', keywords: '捂眼 不看 害羞 monkey'},
      {char: '🙉', keywords: '捂耳 不听 monkey'},
      {char: '🙊', keywords: '捂嘴 不说 monkey'},
    ],
  },
  {
    id: 'gestures',
    icon: '👍',
    label: '手势与人物',
    emojis: [
      {char: '👋', keywords: '挥手 你好 再见 wave hi bye'},
      {char: '🤚', keywords: '举手 停 raised back hand'},
      {char: '🖐️', keywords: '张手 击掌 hand'},
      {char: '✋', keywords: '停 举手 stop high five'},
      {char: '🖖', keywords: '瓦肯举手 vulcan'},
      {char: '👌', keywords: '好的 没问题 ok'},
      {char: '🤌', keywords: '捏手 完美 pinched'},
      {char: '🤏', keywords: '一点点 捏 pinch'},
      {char: '✌️', keywords: '耶 胜利 比耶 peace victory'},
      {char: '🤞', keywords: '祈祷 好运 许愿 fingers crossed'},
      {char: '🫰', keywords: '比心 钞票 finger heart'},
      {char: '🤟', keywords: '爱你 摇滚 love you'},
      {char: '🤘', keywords: '摇滚 酷 rock'},
      {char: '🤙', keywords: '打电话 666 call me'},
      {char: '👈', keywords: '向左 指 left'},
      {char: '👉', keywords: '向右 指 right'},
      {char: '👆', keywords: '向上 指 up'},
      {char: '👇', keywords: '向下 指 down'},
      {char: '☝️', keywords: '第一 注意 指上 one'},
      {char: '👍', keywords: '赞 好的 厉害 thumbs up like good'},
      {char: '👎', keywords: '踩 不行 thumbs down'},
      {char: '✊', keywords: '加油 握拳 fist'},
      {char: '👊', keywords: '碰拳 出拳 punch'},
      {char: '🤛', keywords: '左碰拳 fist'},
      {char: '🤜', keywords: '右碰拳 fist'},
      {char: '👏', keywords: '鼓掌 厉害 欢迎 clap'},
      {char: '🙌', keywords: '举手 欢呼 万岁 hooray'},
      {char: '🫶', keywords: '比心 爱 heart hands'},
      {char: '👐', keywords: '摊手 open hands'},
      {char: '🤲', keywords: '捧起 palms up'},
      {char: '🤝', keywords: '握手 合作 成交 handshake'},
      {char: '🙏', keywords: '拜托 感谢 祈祷 pray please thanks'},
      {char: '✍️', keywords: '写字 记录 write'},
      {char: '💅', keywords: '美甲 优雅 淡定 nail'},
      {char: '💪', keywords: '加油 力量 强壮 muscle strong'},
      {char: '👀', keywords: '吃瓜 围观 看 eyes look'},
      {char: '🧠', keywords: '大脑 聪明 思考 brain'},
      {char: '🫂', keywords: '拥抱 安慰 hug'},
      {char: '🤦', keywords: '捂脸 无奈 facepalm'},
      {char: '🤷', keywords: '摊手 不知道 shrug'},
      {char: '🙆', keywords: '可以 好的 ok person'},
      {char: '🙅', keywords: '不行 拒绝 no person'},
      {char: '🙋', keywords: '举手 提问 raise hand'},
      {char: '🙇', keywords: '鞠躬 道歉 感谢 bow'},
      {char: '💃', keywords: '跳舞 开心 dance'},
      {char: '🕺', keywords: '跳舞 派对 dance'},
      {char: '🏃', keywords: '跑步 冲 run'},
      {char: '🧘', keywords: '冥想 放松 yoga zen'},
    ],
  },
  {
    id: 'animals',
    icon: '🐱',
    label: '动物与自然',
    emojis: [
      {char: '🐶', keywords: '狗 旺财 柴犬 dog puppy'},
      {char: '🐱', keywords: '猫 喵星人 cat kitty'},
      {char: '🐭', keywords: '老鼠 mouse'},
      {char: '🐹', keywords: '仓鼠 hamster'},
      {char: '🐰', keywords: '兔子 rabbit bunny'},
      {char: '🦊', keywords: '狐狸 fox'},
      {char: '🐻', keywords: '小熊 bear'},
      {char: '🐼', keywords: '熊猫 国宝 panda'},
      {char: '🐨', keywords: '考拉 koala'},
      {char: '🐯', keywords: '老虎 tiger'},
      {char: '🦁', keywords: '狮子 lion'},
      {char: '🐮', keywords: '奶牛 牛 cow'},
      {char: '🐷', keywords: '小猪 猪 pig'},
      {char: '🐸', keywords: '青蛙 蛙 frog'},
      {char: '🐵', keywords: '猴子 monkey'},
      {char: '🐔', keywords: '鸡 chicken'},
      {char: '🐧', keywords: '企鹅 penguin'},
      {char: '🐦', keywords: '小鸟 bird'},
      {char: '🐤', keywords: '小鸡 chick'},
      {char: '🦆', keywords: '鸭子 冲鸭 duck'},
      {char: '🦉', keywords: '猫头鹰 熬夜 owl'},
      {char: '🦄', keywords: '独角兽 unicorn'},
      {char: '🐝', keywords: '蜜蜂 bee'},
      {char: '🦋', keywords: '蝴蝶 butterfly'},
      {char: '🐢', keywords: '乌龟 慢 turtle'},
      {char: '🐍', keywords: '蛇 snake'},
      {char: '🦖', keywords: '恐龙 霸王龙 dino'},
      {char: '🐳', keywords: '鲸鱼 whale'},
      {char: '🐬', keywords: '海豚 dolphin'},
      {char: '🐟', keywords: '鱼 摸鱼 fish'},
      {char: '🦈', keywords: '鲨鱼 shark'},
      {char: '🐙', keywords: '章鱼 octopus'},
      {char: '🌸', keywords: '樱花 花 浪漫 sakura flower'},
      {char: '🌹', keywords: '玫瑰 花 爱 rose'},
      {char: '🌻', keywords: '向日葵 阳光 sunflower'},
      {char: '🌷', keywords: '郁金香 tulip'},
      {char: '🍀', keywords: '四叶草 好运 clover luck'},
      {char: '🌵', keywords: '仙人掌 cactus'},
      {char: '🍁', keywords: '枫叶 秋天 maple'},
      {char: '☀️', keywords: '太阳 晴天 阳光 sun'},
      {char: '🌤️', keywords: '多云 晴 cloudy'},
      {char: '🌧️', keywords: '下雨 雨天 rain'},
      {char: '⛈️', keywords: '雷雨 闪电 storm'},
      {char: '❄️', keywords: '雪花 下雪 冬天 snow'},
      {char: '🌈', keywords: '彩虹 好心情 rainbow'},
      {char: '🌙', keywords: '月亮 晚安 夜晚 moon night'},
      {char: '⭐', keywords: '星星 收藏 star'},
      {char: '🌊', keywords: '海浪 冲浪 wave'},
    ],
  },
  {
    id: 'food',
    icon: '🧋',
    label: '美食与饮品',
    emojis: [
      {char: '☕', keywords: '咖啡 提神 续命 coffee'},
      {char: '🍵', keywords: '绿茶 喝茶 吃瓜 tea'},
      {char: '🧋', keywords: '奶茶 珍珠奶茶 boba milk tea'},
      {char: '🥤', keywords: '可乐 饮料 肥宅快乐水 soda'},
      {char: '🍺', keywords: '啤酒 干杯 喝酒 beer'},
      {char: '🍻', keywords: '干杯 碰杯 聚会 cheers'},
      {char: '🥂', keywords: '香槟 庆祝 干杯 champagne'},
      {char: '🍷', keywords: '红酒 葡萄酒 wine'},
      {char: '🍉', keywords: '西瓜 吃瓜 watermelon'},
      {char: '🍓', keywords: '草莓 strawberry'},
      {char: '🍑', keywords: '桃子 水蜜桃 peach'},
      {char: '🍒', keywords: '樱桃 车厘子 cherry'},
      {char: '🍇', keywords: '葡萄 grape'},
      {char: '🍊', keywords: '橘子 橙子 orange'},
      {char: '🍋', keywords: '柠檬 酸了 lemon'},
      {char: '🍌', keywords: '香蕉 banana'},
      {char: '🍎', keywords: '苹果 apple'},
      {char: '🥑', keywords: '牛油果 avocado'},
      {char: '🍔', keywords: '汉堡 快餐 burger'},
      {char: '🍟', keywords: '薯条 fries'},
      {char: '🍕', keywords: '披萨 pizza'},
      {char: '🌭', keywords: '热狗 hotdog'},
      {char: '🌮', keywords: '塔可 taco'},
      {char: '🍜', keywords: '拉面 吃面 泡面 ramen noodles'},
      {char: '🍲', keywords: '火锅 干饭 hotpot'},
      {char: '🍛', keywords: '咖喱饭 curry'},
      {char: '🍣', keywords: '寿司 日料 sushi'},
      {char: '🥟', keywords: '饺子 水饺 dumpling'},
      {char: '🍱', keywords: '便当 盒饭 bento'},
      {char: '🍖', keywords: '烤肉 吃肉 meat'},
      {char: '🍗', keywords: '炸鸡 鸡腿 chicken'},
      {char: '🍤', keywords: '天妇罗 炸虾 shrimp'},
      {char: '🍿', keywords: '爆米花 吃瓜 电影 popcorn'},
      {char: '🥐', keywords: '牛角包 面包 croissant'},
      {char: '🍩', keywords: '甜甜圈 donut'},
      {char: '🍪', keywords: '曲奇 饼干 cookie'},
      {char: '🎂', keywords: '生日蛋糕 生日快乐 cake birthday'},
      {char: '🍰', keywords: '蛋糕 甜品 cake'},
      {char: '🧁', keywords: '纸杯蛋糕 cupcake'},
      {char: '🍦', keywords: '冰淇淋 雪糕 ice cream'},
      {char: '🍫', keywords: '巧克力 chocolate'},
      {char: '🍬', keywords: '糖果 candy'},
      {char: '🍡', keywords: '团子 甜点 dango'},
      {char: '🥡', keywords: '外卖 干饭 takeout'},
    ],
  },
  {
    id: 'activities',
    icon: '🎉',
    label: '活动与娱乐',
    emojis: [
      {char: '🎉', keywords: '撒花 庆祝 恭喜 party tada'},
      {char: '🎊', keywords: '礼花 彩带 庆祝 confetti'},
      {char: '🎈', keywords: '气球 派对 balloon'},
      {char: '🎁', keywords: '礼物 惊喜 gift'},
      {char: '🎆', keywords: '烟花 新年 fireworks'},
      {char: '🎇', keywords: '烟火 仙女棒 sparkler'},
      {char: '🧧', keywords: '红包 发财 过年 red envelope'},
      {char: '🏆', keywords: '奖杯 冠军 第一 trophy win'},
      {char: '🥇', keywords: '金牌 第一名 gold medal'},
      {char: '🎯', keywords: '目标 命中 精准 target'},
      {char: '🎮', keywords: '游戏 手柄 开黑 game'},
      {char: '🕹️', keywords: '街机 游戏 joystick'},
      {char: '🎲', keywords: '骰子 运气 dice'},
      {char: '🧩', keywords: '拼图 解谜 puzzle'},
      {char: '🎸', keywords: '吉他 乐队 guitar'},
      {char: '🎵', keywords: '音乐 歌 music'},
      {char: '🎶', keywords: '音符 唱歌 notes'},
      {char: '🎤', keywords: '麦克风 唱歌 K歌 mic'},
      {char: '🎧', keywords: '耳机 听歌 headphones'},
      {char: '🎬', keywords: '电影 拍片 movie'},
      {char: '🎨', keywords: '画画 设计 艺术 art'},
      {char: '📸', keywords: '拍照 相机 camera'},
      {char: '⚽', keywords: '足球 football soccer'},
      {char: '🏀', keywords: '篮球 basketball'},
      {char: '🏓', keywords: '乒乓球 ping pong'},
      {char: '🏸', keywords: '羽毛球 badminton'},
      {char: '🎾', keywords: '网球 tennis'},
      {char: '🚴', keywords: '骑行 自行车 bike'},
      {char: '🏊', keywords: '游泳 swim'},
      {char: '⛷️', keywords: '滑雪 ski'},
      {char: '🏕️', keywords: '露营 户外 camping'},
      {char: '✈️', keywords: '飞机 旅行 出差 flight travel'},
      {char: '🚗', keywords: '汽车 开车 car'},
      {char: '🚄', keywords: '高铁 出行 train'},
      {char: '🚀', keywords: '火箭 起飞 上线 rocket launch'},
      {char: '🛸', keywords: '飞碟 ufo'},
    ],
  },
  {
    id: 'objects',
    icon: '💻',
    label: '办公与物品',
    emojis: [
      {char: '💻', keywords: '电脑 写代码 办公 laptop code'},
      {char: '🖥️', keywords: '显示器 电脑 desktop'},
      {char: '⌨️', keywords: '键盘 打字 keyboard'},
      {char: '📱', keywords: '手机 phone'},
      {char: '💡', keywords: '灯泡 灵感 主意 idea bulb'},
      {char: '🔦', keywords: '手电筒 flashlight'},
      {char: '📌', keywords: '图钉 置顶 标记 pin'},
      {char: '📍', keywords: '定位 坐标 location'},
      {char: '📎', keywords: '回形针 附件 clip'},
      {char: '📝', keywords: '笔记 记录 备忘 memo note'},
      {char: '📚', keywords: '书本 学习 看书 books study'},
      {char: '📖', keywords: '阅读 书 book'},
      {char: '📅', keywords: '日历 日程 calendar'},
      {char: '⏰', keywords: '闹钟 提醒 时间 alarm clock'},
      {char: '⏳', keywords: '沙漏 等待 倒计时 hourglass'},
      {char: '🔍', keywords: '搜索 查找 放大镜 search'},
      {char: '🔑', keywords: '钥匙 密码 关键 key'},
      {char: '🔒', keywords: '锁 私密 安全 lock private'},
      {char: '🔓', keywords: '解锁 公开 unlock'},
      {char: '🔔', keywords: '铃铛 通知 提醒 bell'},
      {char: '📢', keywords: '喇叭 公告 通知 megaphone'},
      {char: '💬', keywords: '聊天 对话 消息 chat'},
      {char: '💭', keywords: '想法 思考 thought'},
      {char: '✉️', keywords: '邮件 信封 mail'},
      {char: '📦', keywords: '快递 包裹 打包 package'},
      {char: '🛠️', keywords: '工具 修复 维护 tools fix'},
      {char: '⚙️', keywords: '设置 齿轮 settings'},
      {char: '🧪', keywords: '实验 测试 test'},
      {char: '🐛', keywords: '虫子 bug 调试 debug'},
      {char: '💰', keywords: '钱袋 发财 赚钱 money'},
      {char: '💸', keywords: '花钱 飞了 破费 money wings'},
      {char: '💎', keywords: '钻石 珍贵 diamond'},
      {char: '👑', keywords: '皇冠 王者 大佬 crown king'},
      {char: '🕶️', keywords: '墨镜 酷 sunglasses'},
      {char: '☂️', keywords: '雨伞 umbrella'},
      {char: '🧸', keywords: '泰迪熊 玩具 teddy'},
    ],
  },
  {
    id: 'symbols',
    icon: '❤️',
    label: '爱心与符号',
    emojis: [
      {char: '❤️', keywords: '红心 爱 喜欢 heart love'},
      {char: '🧡', keywords: '橙心 爱 orange heart'},
      {char: '💛', keywords: '黄心 爱 yellow heart'},
      {char: '💚', keywords: '绿心 爱 green heart'},
      {char: '💙', keywords: '蓝心 爱 blue heart'},
      {char: '💜', keywords: '紫心 爱 purple heart'},
      {char: '🤍', keywords: '白心 爱 white heart'},
      {char: '🖤', keywords: '黑心 爱 black heart'},
      {char: '🩷', keywords: '粉心 爱 pink heart'},
      {char: '❤️‍🔥', keywords: '燃烧的心 热爱 heart on fire'},
      {char: '💔', keywords: '心碎 难过 broken heart'},
      {char: '💕', keywords: '双心 甜蜜 two hearts'},
      {char: '💖', keywords: '闪亮爱心 sparkling heart'},
      {char: '💘', keywords: '一见钟情 丘比特 cupid'},
      {char: '✨', keywords: '闪耀 星星 漂亮 sparkles'},
      {char: '🌟', keywords: '闪星 优秀 glowing star'},
      {char: '💫', keywords: '流星 晕 dizzy star'},
      {char: '🔥', keywords: '火 热门 厉害 fire hot lit'},
      {char: '⚡', keywords: '闪电 快 能量 zap lightning'},
      {char: '💥', keywords: '爆炸 冲突 boom'},
      {char: '💯', keywords: '满分 100 同意 100'},
      {char: '💢', keywords: '生气 青筋 anger'},
      {char: '💦', keywords: '汗 滴水 sweat'},
      {char: '💤', keywords: '睡觉 困 zzz'},
      {char: '✅', keywords: '对勾 完成 通过 check done ok'},
      {char: '❌', keywords: '叉号 错误 取消 cross no'},
      {char: '⭕', keywords: '圈 正确 circle'},
      {char: '⚠️', keywords: '警告 注意 warning'},
      {char: '❓', keywords: '问号 疑惑 question'},
      {char: '❗', keywords: '感叹号 重要 exclamation'},
      {char: '⁉️', keywords: '震惊 疑问 interrobang'},
      {char: '➕', keywords: '加一 +1 plus'},
      {char: '🆕', keywords: '新 new'},
      {char: '🆗', keywords: '好的 ok'},
      {char: '🆙', keywords: '上升 顶 up'},
      {char: '🆒', keywords: '酷 cool'},
    ],
  },
];

const ALL_EMOJIS: EmojiItem[] = CATEGORIES.flatMap((c) => c.emojis);

const DEFAULT_QUICK_EMOJIS = [
  '😂', '🤣', '😄', '😊', '🥰', '😎', '🤔', '😅',
  '😭', '🥹', '🤯', '🥳', '👍', '👏', '🙏', '🫶',
  '🤝', '👌', '👀', '❤️', '🔥', '✨', '🎉', '🧋',
];

function loadRecentEmojis(): string[] {
  try {
    const raw = window.localStorage.getItem(RECENT_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((x): x is string => typeof x === 'string' && x.length > 0).slice(0, MAX_RECENT);
  } catch {
    return [];
  }
}

function saveRecentEmojis(list: string[]): void {
  try {
    window.localStorage.setItem(RECENT_STORAGE_KEY, JSON.stringify(list));
  } catch {
    // ignore storage quota errors
  }
}

const recentEmojis = ref<string[]>(loadRecentEmojis());
const activeTab = ref<string>(recentEmojis.value.length > 0 ? 'recent' : 'faces');
const searchQuery = ref('');
const pickerRef = ref<HTMLElement | null>(null);
const gridRef = ref<HTMLElement | null>(null);

const tabs = computed(() => [
  {id: 'recent', icon: '🕒', label: '最近常用'},
  ...CATEGORIES.map((c) => ({id: c.id, icon: c.icon, label: c.label})),
]);

const trimmedQuery = computed(() => searchQuery.value.trim().toLowerCase());

const displayedEmojis = computed<string[]>(() => {
  if (trimmedQuery.value) {
    const q = trimmedQuery.value;
    const seen = new Set<string>();
    const results: string[] = [];
    for (const item of ALL_EMOJIS) {
      if (item.char.includes(q) || item.keywords.toLowerCase().includes(q)) {
        if (!seen.has(item.char)) {
          seen.add(item.char);
          results.push(item.char);
        }
      }
    }
    return results;
  }

  if (activeTab.value === 'recent') {
    return recentEmojis.value.length > 0 ? recentEmojis.value : DEFAULT_QUICK_EMOJIS;
  }

  const cat = CATEGORIES.find((c) => c.id === activeTab.value);
  return (cat ?? CATEGORIES[0]).emojis.map((e) => e.char);
});

const sectionTitle = computed(() => {
  if (trimmedQuery.value) {
    return `搜索结果 (${displayedEmojis.value.length})`;
  }
  if (activeTab.value === 'recent') {
    return recentEmojis.value.length > 0 ? '最近使用' : '高频推荐';
  }
  return CATEGORIES.find((c) => c.id === activeTab.value)?.label ?? '表情';
});

function selectTab(tabId: string): void {
  activeTab.value = tabId;
  searchQuery.value = '';
  if (gridRef.value) {
    gridRef.value.scrollTop = 0;
  }
}

function handleSelectEmoji(emoji: string): void {
  const next = [emoji, ...recentEmojis.value.filter((e) => e !== emoji)].slice(0, MAX_RECENT);
  recentEmojis.value = next;
  saveRecentEmojis(next);
  emit('select', emoji);
}

function handleKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    emit('close');
  }
}

function handleClickOutside(e: MouseEvent): void {
  const target = e.target as HTMLElement | null;
  if (!pickerRef.value || !target) return;
  if (!pickerRef.value.contains(target) && !target.closest('[data-emoji-trigger]')) {
    emit('close');
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown);
  document.addEventListener('mousedown', handleClickOutside);
});

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown);
  document.removeEventListener('mousedown', handleClickOutside);
});
</script>

<template>
  <div
    ref="pickerRef"
    role="dialog"
    aria-label="选择表情"
    class="w-[336px] max-w-[calc(100vw-2rem)] rounded-[22px] ios-glass-popover ring-1 ring-black/[0.07] dark:ring-white/[0.11] shadow-[0_20px_50px_-12px_rgba(0,0,0,0.18)] dark:shadow-[0_24px_60px_-12px_rgba(0,0,0,0.65)] overflow-hidden select-none p-2.5"
  >
    <!-- Search bar -->
    <div class="relative mb-2">
      <Search class="w-3.5 h-3.5 text-[#8E8E93] absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
      <input
        v-model="searchQuery"
        type="text"
        placeholder="搜索表情（如：笑、赞、奶茶、猫）"
        class="w-full h-8 pl-8 pr-7 rounded-[10px] text-[12px] bg-black/[0.05] text-[#1C1C1E] placeholder:text-[#8E8E93] focus:outline-none focus:ring-2 focus:ring-[#007AFF]/40 dark:bg-white/[0.09] dark:text-[#F5F5F7] dark:placeholder:text-[#8E8E93] dark:focus:ring-[#0A84FF]/50 transition-all"
      />
      <button
        v-if="searchQuery"
        type="button"
        @click="searchQuery = ''"
        aria-label="清空搜索"
        class="absolute right-2 top-1/2 -translate-y-1/2 p-0.5 rounded-full text-[#8E8E93] hover:text-[#1C1C1E] dark:hover:text-[#F5F5F7] cursor-pointer"
      >
        <X class="w-3.5 h-3.5" />
      </button>
    </div>

    <!-- Category icon bar -->
    <div class="flex items-center gap-0.5 p-0.5 rounded-[11px] bg-black/[0.05] dark:bg-white/[0.08] mb-2">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        type="button"
        :title="tab.label"
        :aria-label="tab.label"
        @click="selectTab(tab.id)"
        class="flex-1 h-7 flex items-center justify-center rounded-[8px] text-[15px] transition-all duration-150 cursor-pointer"
        :class="
          !trimmedQuery && activeTab === tab.id
            ? 'bg-white shadow-[0_1px_3px_rgba(0,0,0,0.1)] dark:bg-[#3A3A3C] dark:shadow-[0_1px_4px_rgba(0,0,0,0.4)] scale-[1.03]'
            : 'opacity-70 hover:opacity-100 hover:bg-black/[0.04] dark:hover:bg-white/[0.06]'
        "
      >
        {{ tab.icon }}
      </button>
    </div>

    <!-- Section label -->
    <div class="px-1.5 pb-1.5 flex items-center justify-between">
      <span class="text-[11px] font-medium text-[#8E8E93]">
        {{ sectionTitle }}
      </span>
    </div>

    <!-- Scrollable emoji grid -->
    <div ref="gridRef" class="h-[224px] overflow-y-auto chat-scroll pr-0.5">
      <div
        v-if="displayedEmojis.length === 0"
        class="h-full flex items-center justify-center text-[12px] text-[#8E8E93]"
      >
        未找到匹配的表情
      </div>
      <div v-else class="grid grid-cols-8 gap-0.5">
        <button
          v-for="emoji in displayedEmojis"
          :key="emoji"
          type="button"
          @click="handleSelectEmoji(emoji)"
          class="h-9 w-9 flex items-center justify-center rounded-[10px] text-[20px] hover:bg-black/[0.06] hover:scale-110 active:scale-90 dark:hover:bg-white/[0.1] transition-transform duration-100 cursor-pointer"
        >
          {{ emoji }}
        </button>
      </div>
    </div>
  </div>
</template>
