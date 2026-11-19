(()=>{const {parseRecordTime}=window.MeowStatsUtils;
function activityAnalytics(records, now=new Date()) {
    const days=[]
    const dayKey=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
    for(let i=27;i>=0;i--){const d=new Date(now);d.setDate(d.getDate()-i);days.push({date:dayKey(d),count:0})}
    const dayMap=new Map(days.map(d=>[d.date,d]))
    const heat=Array.from({length:7},()=>new Array(24).fill(0))
    const weekdays=new Array(7).fill(0)
    const campuses=new Map(),species=new Map()
    for(const record of records){
        const time=parseRecordTime(record)
        if(time>0){const d=new Date(time);if(dayMap.has(dayKey(d)))dayMap.get(dayKey(d)).count++;const weekday=(d.getDay()+6)%7;heat[weekday][d.getHours()]++;weekdays[weekday]++}
        const area=record.university||String(record.area||'').split('·')[0]||'未标注校园';campuses.set(area,(campuses.get(area)||0)+1)
        const breed=record.species||'未识别品种';species.set(breed,(species.get(breed)||0)+1)
    }
    const rows=map=>[...map].map(([name,count])=>({name,count})).sort((a,b)=>b.count-a.count||a.name.localeCompare(b.name,'zh-CN'))
    return {days,heat,weekdays:weekdays.map((count,i)=>({name:['周一','周二','周三','周四','周五','周六','周日'][i],count})),campuses:rows(campuses),species:rows(species),demoCount:records.filter(r=>r.is_demo).length}
}

window.MeowActivity={activityAnalytics};})();
