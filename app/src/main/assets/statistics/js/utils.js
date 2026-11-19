(()=>{
// src/backend/utils.js
// 纯函数工具集：不依赖 Electron，可被主进程、渲染进程和测试共同使用

// 转义 HTML 特殊字符，防止用户输入被拼进 innerHTML 后注入标签
function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;')
}

// 转义后的值用于 HTML 属性（如 src/href）时，额外拦截危险伪协议
function safeResourceUrl(value) {
    const trimmed = String(value ?? '').trim()
    if (/^\s*(javascript|vbscript)\s*:/i.test(trimmed)) return ''
    // data: 协议只放行图片类型，阻断 data:text/html 等注入载体
    if (/^data:/i.test(trimmed) && !/^data:\s*image\/(png|jpe?g|gif|webp|bmp)\s*;/i.test(trimmed)) return ''
    return escapeHtml(trimmed)
}

// 根据文件扩展名返回 MIME 类型
function getMimeType(filePath) {
    const ext = String(filePath).toLowerCase().split('.').pop()
    const map = {
        jpg: 'image/jpeg',
        jpeg: 'image/jpeg',
        png: 'image/png',
        webp: 'image/webp',
        gif: 'image/gif',
        bmp: 'image/bmp'
    }
    return map[ext] || 'image/jpeg'
}

// 在记录数组中定位一条记录：优先按 id 匹配，找不到再退回下标。
// 修复了“搜索过滤后按下标操作会命中错误记录”的问题
function findRecordIndex(records, { index, id } = {}) {
    if (!Array.isArray(records)) return -1
    if (id !== undefined && id !== null && id !== '') {
        const byId = records.findIndex(r => String(r.id) === String(id))
        if (byId >= 0) return byId
    }
    if (Number.isInteger(index) && index >= 0 && index < records.length) return index
    return -1
}

// 从记录中解析出时间戳；兼容 time 字符串、created_at 和时间戳形式的 id
function parseRecordTime(record) {
    if (!record) return 0
    if (record.created_at) {
        const t = new Date(record.created_at).getTime()
        if (!Number.isNaN(t)) return t
    }
    if (record.time) {
        const t = new Date(String(record.time).replace(' ', 'T')).getTime()
        if (!Number.isNaN(t)) return t
    }
    if (typeof record.id === 'number' && record.id > 1000000000000) return record.id
    return 0
}

// 数值型的猫咪数量，解析失败按 1 只算
function parseCatCount(value) {
    const n = parseInt(value, 10)
    return Number.isFinite(n) && n > 0 ? n : 1
}

// 猫点聚合：把 radiusMeters 范围内聚集的打卡归为一个位置簇。
// records 需先按时间倒序（簇的 seed 即最新一条）；无法定位的记录自成一组排在尾部。
function clusterRecords(records, radiusMeters, getCoords) {
    const clusters = []
    for (const record of records || []) {
        const coords = getCoords ? getCoords(record) : null
        if (!coords || !Number.isFinite(coords.lat) || !Number.isFinite(coords.lng)) {
            clusters.push({ seed: record, members: [record], center: null })
            continue
        }
        let target = null
        for (const cluster of clusters) {
            if (!cluster.center) continue
            if (haversineMeters(coords.lat, coords.lng, cluster.center.lat, cluster.center.lng) <= radiusMeters) {
                target = cluster
                break
            }
        }
        if (target) {
            target.members.push(record)
        } else {
            clusters.push({ seed: record, members: [record], center: { lat: coords.lat, lng: coords.lng } })
        }
    }
    return clusters
}

// 猫咪出没时段划分（按记录时间的小时数）
const TIME_BUCKETS = [
    { label: '清晨 5-9点', from: 5, to: 8 },
    { label: '上午 9-12点', from: 9, to: 11 },
    { label: '午后 12-15点', from: 12, to: 14 },
    { label: '傍晚 15-19点', from: 15, to: 18 },
    { label: '夜间 19-5点', from: 19, to: 4 }
]

function bucketIndexForHour(hour) {
    for (let i = 0; i < TIME_BUCKETS.length; i++) {
        const { from, to } = TIME_BUCKETS[i]
        if (from <= to ? (hour >= from && hour <= to) : (hour >= from || hour <= to)) return i
    }
    return -1
}

function topN(countMap, n) {
    return Object.entries(countMap)
        .map(([name, count]) => ({ name, count }))
        .sort((a, b) => b.count - a.count)
        .slice(0, n)
}

// 统计面板数据：观察记录（records）+ 地图打卡（mapRecords）+ 猫咪档案（cats）
function computeStats(records = [], mapRecords = [], cats = [], now = new Date()) {
    const all = [...(Array.isArray(records) ? records : []), ...(Array.isArray(mapRecords) ? mapRecords : [])]

    const monthPrefix = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime()

    let totalCatsSeen = 0
    let monthCatsSeen = 0
    const locationCount = {}
    const speciesCount = {}
    const areaCount = {}
    const monthlyCount = {}
    const bucketCounts = new Array(TIME_BUCKETS.length).fill(0)

    for (const record of all) {
        const count = parseCatCount(record.count)
        totalCatsSeen += count

        const t = parseRecordTime(record)
        if (t >= monthStart) monthCatsSeen += count
        if (t > 0) {
            const d = new Date(t)
            const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
            monthlyCount[key] = (monthlyCount[key] || 0) + 1
            const bucketIndex = bucketIndexForHour(d.getHours())
            if (bucketIndex >= 0) bucketCounts[bucketIndex]++
        }

        const location = String(record.location || record.title || '').trim()
        if (location) locationCount[location] = (locationCount[location] || 0) + 1

        const species = String(record.species || '').trim()
        if (species) speciesCount[species] = (speciesCount[species] || 0) + 1

        // 所在高校区域（新增打点时自动判定写入；旧数据由调用方回填后传入）
        const area = String(record.area || '').trim()
        if (area) areaCount[area] = (areaCount[area] || 0) + 1
    }

    // 最近 6 个月的记录条数趋势（含空月份）
    const monthly = []
    for (let i = 5; i >= 0; i--) {
        const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
        monthly.push({ month: key, count: monthlyCount[key] || 0 })
    }

    return {
        recordCount: all.length,
        personalCount: Array.isArray(records) ? records.length : 0,
        mapCount: Array.isArray(mapRecords) ? mapRecords.length : 0,
        catProfileCount: Array.isArray(cats) ? cats.length : 0,
        totalCatsSeen,
        monthCatsSeen,
        monthLabel: monthPrefix,
        topLocations: topN(locationCount, 5),
        topSpecies: topN(speciesCount, 5),
        topAreas: topN(areaCount, 5),
        timeBuckets: TIME_BUCKETS.map((bucket, index) => ({ name: bucket.label, count: bucketCounts[index] })),
        monthly
    }
}

// ==========================================
// 地理计算：校园范围判定
// ==========================================

const GCJ_A = 6378245.0
const GCJ_EE = 0.00669342162296594323

function outOfChina(lat, lng) {
    return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function transformLat(x, y) {
    let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
    ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
    ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
    ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0
    return ret
}

function transformLng(x, y) {
    let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
    ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
    ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
    ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
    return ret
}

// WGS84（GPS/OSM）→ GCJ-02（高德底图坐标系）。校园边界数据以 WGS84 存储，
// 渲染与判定前统一转到 GCJ-02，保证和高德瓦片、用户打点在同一坐标系下比较
function wgs84ToGcj02(lat, lng) {
    if (outOfChina(lat, lng)) return [lat, lng]
    let dLat = transformLat(lng - 105.0, lat - 35.0)
    let dLng = transformLng(lng - 105.0, lat - 35.0)
    const radLat = lat / 180.0 * Math.PI
    let magic = Math.sin(radLat)
    magic = 1 - GCJ_EE * magic * magic
    const sqrtMagic = Math.sqrt(magic)
    dLat = (dLat * 180.0) / ((GCJ_A * (1 - GCJ_EE)) / (magic * sqrtMagic) * Math.PI)
    dLng = (dLng * 180.0) / (GCJ_A / sqrtMagic * Math.cos(radLat) * Math.PI)
    return [lat + dLat, lng + dLng]
}

// 射线法：判断点是否在多边形内（ring 为 [lat, lng] 数组，首尾可不闭合）
function pointInPolygon(lat, lng, ring) {
    if (!Array.isArray(ring) || ring.length < 3) return false
    let inside = false
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
        const [latI, lngI] = ring[i]
        const [latJ, lngJ] = ring[j]
        const intersects = (lngI > lng) !== (lngJ > lng) &&
            lat < (latJ - latI) * (lng - lngI) / (lngJ - lngI) + latI
        if (intersects) inside = !inside
    }
    return inside
}

// 两点间的地表距离（米），用于“最近的学校”提示
function haversineMeters(lat1, lng1, lat2, lng2) {
    const rad = Math.PI / 180
    const dLat = (lat2 - lat1) * rad
    const dLng = (lng2 - lng1) * rad
    const a = Math.sin(dLat / 2) ** 2 +
        Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2
    return 6371000 * 2 * Math.asin(Math.sqrt(a))
}

// 校区/片区的多边形环数组：优先用转换后的 GCJ-02 环（应用运行时），
// 其次 WGS84 原始环（测试/构建场景）；兼容旧的单环 polygon 字段
function polygonsOf(feature) {
    if (!feature) return []
    if (Array.isArray(feature.polygons_gcj02) && feature.polygons_gcj02.length > 0) return feature.polygons_gcj02
    if (Array.isArray(feature.polygons)) return feature.polygons
    if (Array.isArray(feature.polygon_gcj02)) return [feature.polygon_gcj02]
    if (Array.isArray(feature.polygon)) return [feature.polygon]
    return []
}

// 用鞋带公式估算环的面积（平方米），用于挑选校区的“主环”
function ringAreaMeters2(ring) {
    if (!Array.isArray(ring) || ring.length < 3) return 0
    const lats = ring.map(p => p[0])
    const meanLat = lats.reduce((s, v) => s + v, 0) / lats.length
    const MX = 111320 * Math.cos(meanLat * Math.PI / 180)
    const MY = 110540
    let sum = 0
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
        const [latI, lngI] = ring[i]
        const [latJ, lngJ] = ring[j]
        sum += (lngJ * MX) * (latI * MY) - (lngI * MX) * (latJ * MY)
    }
    return Math.abs(sum) / 2
}

// 挑选校区的主环（面积最大的外环），用于取中心点等
function mainRingOf(feature) {
    const rings = polygonsOf(feature)
    if (rings.length === 0) return null
    return rings.reduce((a, b) => ringAreaMeters2(b) > ringAreaMeters2(a) ? b : a)
}

// 在高校范围数据中判定坐标归属，优先返回最细粒度（片区 > 校区 > 高校）
function detectArea(areasData, lat, lng) {
    if (!areasData || !Array.isArray(areasData.universities)) return null
    for (const uni of areasData.universities) {
        for (const campus of uni.campuses || []) {
            const rings = polygonsOf(campus).map(ring => ring.polygon_gcj02 || ring)
            if (!rings.some(ring => pointInPolygon(lat, lng, ring))) continue
            if (Array.isArray(campus.zones)) {
                for (const zone of campus.zones || []) {
                    const zoneRings = polygonsOf(zone).map(ring => ring.polygon_gcj02 || ring)
                    if (zoneRings.some(ring => pointInPolygon(lat, lng, ring))) {
                        return { university: uni.name, campus: campus.name, zone: zone.name }
                    }
                }
            }
            return { university: uni.name, campus: campus.name, zone: null }
        }
    }
    return null
}

// 校外时给出最近的校园参考，如「暨南大学·石牌校区 附近约 1.2km」
function nearestAreaLabel(areasData, lat, lng, maxMeters = 3000) {
    if (!areasData || !Array.isArray(areasData.universities)) return null
    let best = null
    for (const uni of areasData.universities) {
        for (const campus of uni.campuses || []) {
            const mainRing = mainRingOf(campus)
            if (!mainRing || mainRing.length === 0) continue
            const centerLat = mainRing.reduce((s, p) => s + p[0], 0) / mainRing.length
            const centerLng = mainRing.reduce((s, p) => s + p[1], 0) / mainRing.length
            const dist = haversineMeters(lat, lng, centerLat, centerLng)
            if (!best || dist < best.dist) best = { university: uni.name, campus: campus.name, dist }
        }
    }
    if (!best || best.dist > maxMeters) return null
    const km = best.dist >= 1000 ? `${(best.dist / 1000).toFixed(1)}km` : `${Math.round(best.dist / 100) * 100}m`
    return `${best.university}·${best.campus} 附近约 ${km}`
}

function formatArea(match) {
    if (!match) return ''
    return [match.university, match.campus, match.zone].filter(Boolean).join('·')
}

window.MeowStatsUtils = {
    escapeHtml,
    safeResourceUrl,
    getMimeType,
    findRecordIndex,
    parseRecordTime,
    parseCatCount,
    computeStats,
    wgs84ToGcj02,
    pointInPolygon,
    haversineMeters,
    detectArea,
    nearestAreaLabel,
    formatArea,
    polygonsOf,
    ringAreaMeters2,
    mainRingOf,
    clusterRecords,
    TIME_BUCKETS,
    bucketIndexForHour
}

})();
