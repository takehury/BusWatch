from PIL import Image, ImageDraw, ImageFont, ImageFilter
from pathlib import Path
import math

OUT=Path(__file__).parent
ROOT=OUT.parents[2]
FONT='/System/Library/Fonts/Supplemental/Arial.ttf'
BOLD='/System/Library/Fonts/Supplemental/Arial Bold.ttf'
if not Path(FONT).exists(): FONT='/System/Library/Fonts/Supplemental/Verdana.ttf'; BOLD='/System/Library/Fonts/Supplemental/Verdana Bold.ttf'

def f(size,b=False): return ImageFont.truetype(BOLD if b else FONT,size)
def txt(d,xy,s,size,fill='#F4F7F8',b=False,anchor=None): d.text(xy,s,font=f(size,b),fill=fill,anchor=anchor)
def rounded(d,box,r,fill,outline=None,w=1): d.rounded_rectangle(box,r,fill=fill,outline=outline,width=w)

def skyline(im, horizon):
    d=ImageDraw.Draw(im,'RGBA'); W,H=im.size
    # Neva twilight gradient glow
    for y in range(horizon,H):
        a=int(80*(y-horizon)/(H-horizon)); d.line((0,y,W,y),fill=(8,37,53,a))
    # water strokes
    for y in range(horizon+35,H,26): d.line((0,y,W,y),fill=(57,122,133,55),width=2)
    # distant roofs
    col=(15,30,43,230)
    d.polygon([(0,horizon),(80,horizon-30),(125,horizon-10),(205,horizon-45),(280,horizon),(380,horizon-24),(440,horizon),(520,horizon-35),(580,horizon),(665,horizon-50),(730,horizon),(805,horizon-28),(900,horizon),(980,horizon-18),(1060,horizon),(1150,horizon-40),(1230,horizon),(1320,horizon-25),(1400,horizon),(1500,horizon-46),(1600,horizon),(1600,H),(0,H)],fill=col)
    # golden windows
    for x,y in [(90,horizon-12),(215,horizon-28),(390,horizon-10),(540,horizon-17),(700,horizon-31),(820,horizon-13),(1040,horizon-8),(1170,horizon-23),(1370,horizon-19),(1510,horizon-27)]: d.rectangle((x,y,x+7,y+18),fill=(240,177,76,180))
    # palace/rostral-column-like skyline and spire
    cx=int(W*.72); base=horizon+35
    d.rectangle((cx-110,base-145,cx+110,base),fill=(20,39,52,240))
    d.polygon([(cx-135,base-145),(cx-100,base-180),(cx+100,base-180),(cx+135,base-145)],fill=(22,42,55,240))
    d.polygon([(cx-15,base-180),(cx,base-310),(cx+15,base-180)],fill=(18,36,49,245))
    d.ellipse((cx-9,base-325,cx+9,base-307),fill=(250,169,70,255))
    for x in range(cx-78,cx+90,26): d.rectangle((x,base-125,x+10,base-78),fill=(242,174,73,180))
    # bridge arc
    d.arc((int(W*.04),horizon-10,int(W*.48),horizon+170),180,350,fill=(95,162,167,150),width=4)
    d.line((int(W*.04),horizon+72,int(W*.48),horizon+72),fill=(91,146,158,120),width=3)

def app_card(size, mode='routes'):
    w,h=size; im=Image.new('RGB',size,'#101d2b'); d=ImageDraw.Draw(im)
    rounded(d,(0,0,w-1,h-1),28,'#13283a', '#416277',3)
    txt(d,(w//2,40),'BUS WATCH',20,'#87b7c1',True,'mm'); txt(d,(30,82),'Санкт-Петербург',30,'#f6f8f5',True)
    txt(d,(30,123),'МАРШРУТЫ И ОСТАНОВКИ',15,'#8ba8b3',True)
    if mode=='routes':
      rows=[('24','Комендантский проспект','4 мин'),('27','Белорусская улица','7 мин'),('39','Пулково — Московская','11 мин'),('181','Кронштадтская площадь','14 мин')]
    else:
      rows=[('27','Белорусская улица','7 мин'),('39','Пулково — Московская','11 мин'),('3','Финляндский вокзал','16 мин')]
    y=165
    for n,name,eta in rows:
      rounded(d,(22,y,w-22,y+72),18,'#1b3548'); d.ellipse((37,y+15,81,y+59),fill='#f3a84d'); txt(d,(59,y+37),n,17,'#102131',True,'mm'); txt(d,(98,y+19),name,19,'#f1f4f1',True); txt(d,(98,y+46),'автобус',14,'#89aeb7'); txt(d,(w-42,y+34),eta,17,'#f3b35b',True,'rm'); y+=86
    rounded(d,(22,h-72,w-22,h-25),18,'#f0a34c'); txt(d,(w//2,h-49),'ОБНОВИТЬ ДАННЫЕ',14,'#162534',True,'mm')
    return im

def phone(im,x,y,w,h):
    d=ImageDraw.Draw(im,'RGBA'); rounded(d,(x,y,x+w,y+h),48,(7,13,21,245),(90,142,156,220),3)
    # Use a real watch render inside the device mockup, never a re-drawn UI.
    sources=[ROOT/'tests/preview/13-spb-routes.png', ROOT/'tests/preview/14-spb-stops.png',
             ROOT/'backups/KrskBusWatch-github-v1.24-2026-11-11/tests/preview/13-spb-routes.png']
    src=next((p for p in sources if p.exists()),None)
    if src:
        shot=Image.open(src).convert('RGB').resize((w-30,h-30),Image.Resampling.LANCZOS)
        mask=Image.new('L',shot.size,0); ImageDraw.Draw(mask).rounded_rectangle((0,0,shot.width-1,shot.height-1),radius=30,fill=255)
        im.paste(shot,(x+15,y+15),mask)
    else:
        inner=app_card((w-30,h-30),'routes').resize((w-30,h-30),Image.Resampling.LANCZOS); im.paste(inner,(x+15,y+15))

def watch(im,cx,cy,r):
    # Real watch UI screenshots from the repository's SPB preview set, composited as a circular display.
    # Prefer the current, real watch renders; fall back to the archived set.
    sources=[ROOT/'tests/preview/13-spb-routes.png', ROOT/'tests/preview/14-spb-stops.png',
             ROOT/'backups/KrskBusWatch-github-v1.24-2026-11-11/tests/preview/13-spb-routes.png',
             ROOT/'backups/KrskBusWatch-github-v1.24-2026-11-11/tests/preview/14-spb-stops.png']
    src=next((p for p in sources if p.exists()),None)
    d=ImageDraw.Draw(im,'RGBA'); d.ellipse((cx-r-10,cy-r-10,cx+r+10,cy+r+10),fill=(6,12,19,255),outline=(96,148,160,255),width=7)
    if src:
      shot=Image.open(src).convert('RGB').resize((2*r-26,2*r-26),Image.Resampling.LANCZOS)
      mask=Image.new('L',shot.size,0); ImageDraw.Draw(mask).ellipse((0,0,shot.width-1,shot.height-1),fill=255)
      im.paste(shot,(cx-r+13,cy-r+13),mask)
    else:
      txt(d,(cx,cy-r+54),'09:41',28,'#f3f6f2',True,'mm')

def hero():
    W,H=1600,900; im=Image.new('RGB',(W,H),'#081522'); d=ImageDraw.Draw(im)
    for y in range(H):
      t=y/H; d.line((0,y,W,y),fill=(8+int(8*t),19+int(28*t),35+int(34*t)))
    skyline(im,610)
    # teal glow
    glow=Image.new('RGBA',(W,H)); gd=ImageDraw.Draw(glow); gd.ellipse((700,220,1420,920),fill=(13,104,114,80)); glow=glow.filter(ImageFilter.GaussianBlur(90)); im=Image.alpha_composite(im.convert('RGBA'),glow); d=ImageDraw.Draw(im)
    txt(d,(82,86),'BUS WATCH  /  ГОРОДСКОЙ РИТМ',24,'#f2ae59',True); txt(d,(82,142),'САНКТ-',66,'#f7f8f4',True); txt(d,(82,210),'ПЕТЕРБУРГ',66,'#f7f8f4',True)
    txt(d,(84,314),'Маршруты города — всегда под рукой',30,'#9dc4c8'); txt(d,(84,365),'Живые автобусы  •  точный ETA  •  остановки рядом',21,'#77a4ad')
    rounded(d,(84,435,430,495),30,'#efa64d'); txt(d,(257,465),'СМОТРИМ, ГДЕ АВТОБУС',17,'#132333',True,'mm')
    phone(im,1010,90,380,680)
    txt(d,(1130,814),'27',42,'#f1ae55',True,'mm'); txt(d,(1168,814),'Белорусская улица',21,'#ecf2ee',False,'lm'); txt(d,(1130,848),'через 7 минут',18,'#9dbcc0',False,'mm')
    im.convert('RGB').save(OUT/'hero-spb-1600x900.png',quality=94)

def square():
    W=H=1200; im=Image.new('RGB',(W,H),'#091725'); d=ImageDraw.Draw(im)
    skyline(im,770); txt(d,(72,68),'САНКТ-ПЕТЕРБУРГ',32,'#f0aa52',True); txt(d,(72,118),'Транспорт без догадок.',57,'#f4f7f3',True); txt(d,(72,188),'Маршруты. Автобусы. ETA.',32,'#a4c9cb')
    phone(im,590,330,445,620); watch(im,240,730,180)
    rounded(d,(72,310,500,410),22,'#17364a', '#39687b',2); txt(d,(105,345),'27  Белорусская улица',24,'#f2f5f0',True); txt(d,(105,382),'7 мин  ·  автобус',20,'#f0aa52')
    txt(d,(72,1110),'BUS WATCH',22,'#84b3bb',True); txt(d,(1128,1110),'живые данные',19,'#8caeb4',False,'ra'); im.save(OUT/'square-spb-1200x1200.png',quality=94)

def portrait():
    W,H=1080,1350; im=Image.new('RGB',(W,H),'#081522'); d=ImageDraw.Draw(im); skyline(im,1030)
    txt(d,(64,64),'BUS WATCH',25,'#f0aa52',True); txt(d,(64,115),'Твой маршрут',53,'#f4f7f3',True); txt(d,(64,177),'в ритме Петербурга',53,'#f4f7f3',True); txt(d,(68,254),'Смотри ETA на телефоне и часах',25,'#a2c5c8')
    phone(im,80,360,560,820); watch(im,830,640,175)
    rounded(d,(690,865,1010,964),22,'#efa64d'); txt(d,(850,900),'27',31,'#122332',True,'mm'); txt(d,(850,940),'7 мин',24,'#122332',True,'mm')
    txt(d,(690,1010),'Маршруты СПБ',25,'#f2f4ef',True); txt(d,(690,1050),'живые автобусы\nточные остановки',21,'#9dc2c6')
    im.save(OUT/'phone-watch-spb-1080x1350.png',quality=94)

def collage():
    W,H=1600,1000; im=Image.new('RGB',(W,H),'#07131f'); d=ImageDraw.Draw(im)
    txt(d,(70,62),'САНКТ-ПЕТЕРБУРГ  /  BUS WATCH',28,'#f0aa52',True); txt(d,(70,115),'Весь город — в одном взгляде',48,'#f4f7f3',True); txt(d,(70,181),'Выбирай маршрут, находи остановку, успевай на автобус.',24,'#9fc4c7')
    cards=[app_card((410,560),'routes'), app_card((410,560),'stops'), app_card((410,560),'routes')]
    xs=[70,595,1120]
    for i,c in enumerate(cards):
      rounded(d,(xs[i]-10,270,xs[i]+420,850),28,'#102638'); im.paste(c.resize((410,560),Image.Resampling.LANCZOS),(xs[i],290))
      txt(d,(xs[i],890),['01  МАРШРУТЫ','02  ОСТАНОВКИ','03  ETA'][i],18,'#f0aa52',True)
    txt(d,(70,950),'Автобусы Петербурга  ·  живые данные  ·  27 / 39 / 181',20,'#86b0b7'); im.save(OUT/'collage-app-screens-spb-1600x1000.png',quality=94)

if __name__=='__main__': hero(); square(); portrait(); collage()
