import json, subprocess
P="/root/tournage/prises/"; A_R=P+"film_a_Raven_47.mp4"; A_D=P+"film_a_Dimitri_Z.mp4"; B_R=P+"film_b_Raven_47.mp4"; B_D=P+"film_b_Dimitri_Z.mp4"
plans=[dict(source=B_R,debut=0.3,duree=2.5,zoom=[1.0,1.22],centre=[0.42,0.45],fondu=[0.6,0]),
 dict(source=A_D,debut=0.0,duree=2.0,vitesse=0.75,zoom=[1.0,1.12],centre=[0.45,0.45]),
 dict(source=A_D,debut=1.6,duree=2.5,zoom=[1.0,1.35],centre=[0.4,0.42]),
 dict(source=A_D,debut=4.2,duree=3.2,zoom=[1.05,1.28],centre=[0.5,0.38],secousse=3),
 dict(source=B_R,debut=7.4,duree=1.0,zoom=[1.1,1.0],flash=True,coup=[0.05]),
 dict(source=A_R,debut=8.4,duree=0.9,zoom=[1.0,1.08],coup=[0.2,0.35,0.5,0.65]),
 dict(source=B_R,debut=8.9,duree=0.5,zoom=[1.15,1.2],centre=[0.5,0.45],coup=[0.05,0.2]),
 dict(source=A_D,debut=9.3,duree=0.8,zoom=[1.1,1.3],centre=[0.3,0.6],coup=[0.05],secousse=4),
 dict(noir=True,duree=0.5),
 dict(source=B_D,debut=10.0,duree=3.0,zoom=[1.0,1.1],fondu=[0.7,0]),
 dict(source=B_D,debut=13.3,duree=2.3,vitesse=0.5,zoom=[1.0,1.18],centre=[0.5,0.4]),
 dict(source=B_R,debut=14.0,duree=2.3,zoom=[1.0,1.15],centre=[0.5,0.5]),
 dict(source=B_R,debut=16.3,duree=2.6,zoom=[1.05,1.25],centre=[0.5,0.45],coup=[0.05]),
 dict(source=B_R,debut=19.15,duree=0.8,zoom=[1.2,1.35],flash=True,coup=[0.0],fondu=[0,0.3])]
t=0; debuts=[]
for p in plans: debuts.append(t); t+=p["duree"]
fin=6.0; total=t+fin
print("plans:", [round(d,2) for d in debuts], "total", round(total,2))
m={"sortie":"heraliz_film_coin.mp4","fps":30,"plans":plans,"son":"son.wav",
 "etalonnage":{"saturation":0.82,"contraste":1.1,"froid":0.05,"vignette":0.32,"grain":4,"luminosite":0.07},
 "textes":[{"debut":0,"fin":2.5,"texte":"UN *DAYZ* DANS !MINECRAFT!","place":"haut"},
  {"debut":5.6,"fin":6.9,"texte":"Il est là...","style":"sous"},
  {"debut":7.5,"fin":9.2,"texte":"Respire... respire...","style":"sous"},
  {"debut":9.5,"fin":10.2,"texte":"Maintenant.","style":"sous"}],
 "fin":{"duree":fin,"lignes":["SERVEUR *DAYZ* MINECRAFT","DISCORD EN *DESCRIPTION*"]}}
json.dump(m,open("/root/tournage/film/montage.json","w"),ensure_ascii=False,indent=1)
def dur(f): return float(subprocess.run(["ffprobe","-v","error","-show_entries","format=duration","-of","csv=p=0",f],capture_output=True,text=True).stdout)
S="/root/son/"; MC=S+"mc/"; V=S+"voix/"
ev=[dict(fichier=MC+"ambient_weather_rain1.ogg",t=0,gain=-15,jusqu=13.4,boucle=True,fondu=[1.2,0.05]),
 dict(fichier=MC+"ambient_weather_thunder1.ogg",t=0.15,gain=-9,vitesse=0.9),
 dict(synth="nappe",t=0,duree=10.2,gain=-13,note=49,monte=True,fondu=[2,0.05]),
 dict(synth="souffle",t=2.5,duree=2.0,gain=-21,periode=[1.5,1.3]),
 dict(synth="souffle",t=6.9,duree=3.3,gain=-13,periode=[0.95,0.5])]
tt=4.5; per=0.95
while tt<10.15:
    ev.append(dict(fichier=S+"heartbeat_0.ogg",t=round(tt,3),gain=-7)); tt+=per; per=max(0.45,per*0.9)
vf="highpass=f=110,aecho=0.8:0.4:35:0.18"
ev+=[dict(fichier=V+"v1.wav",t=5.6,gain=2,filtre=vf),dict(fichier=V+"v2.wav",t=7.5,gain=2,filtre=vf),dict(fichier=V+"v3.wav",t=9.45,gain=3,filtre=vf)]
r=dur(S+"riser_0.ogg"); ev.append(dict(fichier=S+"riser_0.ogg",t=round(10.2-r,3),gain=-8))
ev+=[dict(synth="silence",t=10.12,duree=0.08),
 dict(fichier=S+"whoosh_heavy_0.ogg",t=10.2,gain=-4),dict(fichier=S+"impact_heavy_0.ogg",t=10.2,gain=-2),dict(fichier=S+"drop_boom_0.ogg",t=10.22,gain=-5),
 dict(fichier=MC+"ambient_weather_rain3.ogg",t=10.2,gain=-17,jusqu=13.4,boucle=True)]
for i,x in enumerate((10.35,10.62,10.9)): ev.append(dict(fichier=MC+"step_grass%d.ogg"%(i+1),t=x,gain=-9))
for i,x in enumerate((11.4,11.55,11.7,11.85,12.15,12.3)): ev.append(dict(fichier=S+"ak47_shoot.ogg",t=x,gain=-1,vitesse=(0.97,1.03)[i%2]))
ev+=[dict(fichier=MC+"damage_hit1.ogg",t=12.65,gain=-3),dict(fichier=S+"flesh_0.ogg",t=12.66,gain=-6),dict(fichier=S+"body_fall_0.ogg",t=13.0,gain=-3),
 dict(synth="silence",t=13.4,duree=0.5),dict(synth="acouphene",t=13.4,duree=3.5,gain=-21),
 dict(fichier=MC+"ambient_weather_rain1.ogg",t=13.9,gain=-19,jusqu=24.9,boucle=True,filtre="lowpass=f=700",fondu=[0.8,0.3])]
tt=14.0
while tt<19.1:
    ev.append(dict(fichier=S+"heartbeat_0.ogg",t=round(tt,3),gain=-5,filtre="lowpass=f=500")); tt+=1.15
rs=dur(S+"reverse_swell_0.ogg"); ev.append(dict(fichier=S+"reverse_swell_0.ogg",t=round(19.2-rs,3),gain=-8))
ev+=[dict(fichier=S+"hum_0.ogg",t=19.2,gain=-12,duree=5.0,fondu=[0.3,1]),
 dict(fichier=MC+"mob_warden_heartbeat_1.ogg",t=19.4,gain=-5),dict(fichier=MC+"mob_warden_heartbeat_1.ogg",t=20.3,gain=-5),
 dict(fichier=MC+"mob_guardian_curse.ogg",t=20.0,gain=-11),dict(fichier=S+"choir_0.ogg",t=21.45,gain=-8),
 dict(fichier=MC+"mob_warden_attack_impact_1.ogg",t=21.55,gain=-3),dict(fichier=MC+"block_sculk_shrieker_shriek1.ogg",t=22.1,gain=-13),
 dict(fichier=S+"implosion_0.ogg",t=23.7,gain=-6),dict(fichier=MC+"mob_warden_sonic_boom1.ogg",t=24.1,gain=-2),dict(fichier=MC+"random_glass1.ogg",t=24.12,gain=-9),
 dict(synth="silence",t=24.85,duree=0.1),
 dict(fichier=S+"impact_heavy_0.ogg",t=24.95,gain=-3),dict(synth="nappe",t=24.9,duree=fin,gain=-17,note=41,fondu=[0.3,1.5]),
 dict(fichier=V+"v4.wav",t=25.4,gain=2,filtre="lowpass=f=7500,aecho=0.8:0.6:60:0.25")]
json.dump({"duree":round(total,2),"evenements":ev},open("/root/tournage/film/son.json","w"),indent=1)
