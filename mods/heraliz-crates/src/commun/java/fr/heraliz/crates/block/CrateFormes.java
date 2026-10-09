package fr.heraliz.crates.block;

import java.util.HashMap;
import java.util.Map;

/**
 * FICHIER GENERE par outils/formes_caisses.py (mods/heraliz-crates/outils/FormesCaisses.java) : ne pas modifier a la
 * main, relancer l'outil apres tout changement de modele d'une caisse.
 * <p>Forme de chaque caisse (son vrai modele au repos, en cubes de 2 px), regroupee en boites. Par boite, 6 caracteres :
 * x0 y0 z0 x1 y1 z1 = (caractere - 35) px ; x et z comptes depuis -16 px (le bloc principal est en 16..32), y depuis
 * 0 px. Caisse tournee vers le sud (+Z), comme le modele.
 */
final class CrateFormes {
    private CrateFormes() {
    }

    /** Decalage des caracteres et origine de la grille en x et z (px). */
    static final int BASE = 35, OFF = 16;

    static final Map<String, String> BOXES;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("sujet_n07", "3#+C'K1#-3'IC#-E'I/#/1'GE#/G'G-#1/'GG#1I'G+#3-'GI#3K'G7'+?-G3'/7-C?'3C9C-'53-7C'5E)C/'733C-'=/-G/'C13GE'CI3GC)5E39=--?/G3-/5/C;-/=3G5-1;3C/-5317--A/3G7-C;7G=//?3G3/153C115337C1AI7C+1C-3GI1CK3G731?9C33379C13737CC37E7A/3917EE3?G7A-3A/5E;3C?7GE3CI5E-5?/7CG5?I7A15C77E?5CG7E17739?C77E9?/791;=77C?9E991=;E5939=C=93A=C3955=AA95C=A1993;=C99E;=9;3==C9=9=A=");
        m.put("sylvorn", "5#)A%K1#+5%KA#+E%K/#-1%IE#-G%I-#//%GG#/I%G+#1-%EI#1K%EK#5M%A)#7+%A7#K?%M7%+;'I3%-7'K;%-G'G1%/3'I-%11'EG%3I'CI%9K'C+%=-'A/%E1'G;%GC'IA%IC'K3'-7)EA'-C)E7'/A)EC'1E)E1'33)C/'51)CE';G)E7'E?3G9'G=-I9)/;+E5)193E;)1A3E3)35+EA)3C3C1)535AC)5E5A9+1;9E3+353C5-E7/G73195C;31=9G=33C7A33577C=3A?5E93E;9G95/=91C53E771553797559=CC5=E7A15?37C=73?=A175797?75E9737779A?77C9A17?39AC7?E9A993==C5957=??97C=;39;5=??9;A=A5;39?5=;AA?C9=3??55=57?7A=9C?;3=;5?=?=?A?A7=A=?C");
        m.put("mere_des_ruines", "+#'-))I#'K%E/#)A%KA#+I%G-#//%I+#1-%I)#3+'GK#3O'='#5)'=%#7'%=K#=M%AA#GG'K5#K?%MI%'K'C/%)7'K7%+I'E-%1/'I+%3-'I7%EG)G7%GA'K-''/)+G''K)7+')-)+/')7)-E'+G71/'-5)I;'1=)E)'3/+75'3;3EK'3M)5+'7/)9='7I)E)'A/)G5'E7)I+'G/)I7'GG)I1'I5)K-))1/+G))I+?/)+5+7I)+K/5;)3=/I3)75+E=)7A/GA)9G+?1)=3+?A)?E+E5)E;/G7)G;/I=)G?/IE+)I/+-++1/-G++I-71+-5-1++3//73+953CA+9E-;A+;C3CC+AE-CG-+I/5K--M//=-5?;7A-9C;;//)11-7/)?;3E/)G1+5/-7;3-/3/57;/3?;5G/3I55;/5=3G=/7A3E7/E;3G=/E?3G91%=9)71'9;)=1'?;)51)7;-?1)C77/1+57-C1+E95E11G75/135973175;9A17C;973%99'=3%?9'53'79)?3'A9)/3-573533;5A;35=5C=37A5A33959=A3;C9=73A;5C=3A?5C553;;=;55=7A=57A7?75=;7A=5??7A?7)A;=17+593A7+C97E73G95;75=;?=77?;?77=;=?99'=;)19-5;7A9-E;5A95C;73995;;9;+?=17;-9=/5;77==?;7A==7;9?==;;=?=?");
        m.put("porteur_de_peste", "5#)A%K1#+5%KA#+E%K/#-1%IE#-G%I-#//%GG#/I%G+#1-%EI#1K%E)#5+%AK#5M%?7#K?%M7%-?'I3%/7'I?%/C'G1%13'EC%1I'?+%31'=I%3K'=)%9+';K%9M';-%=1'?/%?1'CC%?G'CC%CE'E7'/?/I5'171E?'1A1E3'35/EA'3C/E1'531AC'5E1A+'71/=E'7K/=9//=1I7/191G=/1?1G3/351CA/3C1C-/711=E/7I1=+/9-1;I/9K1;711?3E51375C?13A5C31555AA15C5A11733?C17E3?733?5C755?7A55777??57C;9?59A7?777?9??79A9;");
        m.put("vote", "'#'O%O/%'GG1-%7I/E3/7CAEC/?I3C95E=;G77E9;G=7E?;GAA;CE?7AA?CC7AC=CE1G/EM1");
        m.put("quete", "5#-A%I1#/5%GA#/E%G-#11%AE#1G%EG#5I%A/#A1%E1%/5'9-%11'3/%31)75%3?'E?%5A5EA%7C5E3%95'E-%?3'AC%?E5E/%A35CE%AG'E1%C3'E7%E?/I1'/3)7/'11)33'15)C5'3=5C='5?5E/'?37AE'AG5C7'C=5E1)3597/)51973)755C/+=37?C+=G7?E+?G7A=-3E95A-5G97/-737=C-7G7=/-C75EE-CG5E3/1C73//3195E/3G957/E?1G/11373C11G7391E=5G553=7A=55A7A35757AA57C7AC5?E7A75A?7C573=9==75A9=/77599A77G9997==9?");
        m.put("event", "'#)-%/3#)C%MI#)O%/-#+3%IC#+I%I)#/-%II#/M%I/#I3%KC#IG%K7#M?%Q)%)-I-I%)MI-5%3AC?+%55+?A%5K)?+%?1)G7%??+AE%?K)GA)7G1=G)9I1;-)?1+EE)CI+E/+753=-+9/3;9+?=-AG/7I19115537-17/3933759;A77C9;-;+IG-);-/C/G;-ME/)C--E/)I++K-II+MK-");
        m.put("gemme", "/#%G'Q-#'/'OG#'I'O)#)-'MI#)M'M'#-)'IM#-O'I%#/''GO#/Q'G+')K)M)'++)KK'+M)K+))/?/G))K?/))++?/K)+M?/9)/=/G7)19-G=)1?-G5)37EC?)3AEC3)55EAA)5CEA1)731?C)7E1?/)91/=E)9G/=))G/?KG)GM?K+)K/?MG)KK?M7-191E=-1?1E9/1=1E713?EC)5)+?+K5)M=+)5K+?MK5KM?M/;+1=/E;+G=-+;/-=1G;/K=1I;EK=GE;IG=K+=E-?G/=I1?K7E5?GA5E77G??E7AG?7G7?I?9I7=O?7I99O==I9?O=9O9=Q=");
        m.put("camo", "'#+O%O%#1'%OO#1Q%O'%+OA35%;7-E?%;A)G7%=?-EI%?K)GA%AI)GK%CM)G)%E+EI=%E?)G'%G)'IE%GI)I?);A-E)A-MC1'A/)C1MA/OC1/C?3GC-EA/GE+EC-GG)EE+GG");
        m.put("sticker", "/#)G%M-#//'GG#/I'G+#1-'EI#1K'E)#5+'AK#5M'A'#9)'=M#9O'=1%)E'M/%+1'KE%+G'K1'7E+?K'7M1?1'E5)KA'EE)K1)G5+IA)GE+I3+7C5=1+93=;C+9E=;I/7K3?E/9I3;/1G39I11I77K1373A9C37EA9357C=;55;A7=35G59I77;?;=37I79K19+7=-/9-3A/595A=73=+7A-3=-5A/3=7CA99=9=A;");
        m.put("build", ")#)M%M)%)1'1E%+M)1I%?M)C)%A1'E9%C=7E)%E/'G)%G-'KI%IM'K+%K-'MI%KK'M+'+1E/+'//I1+'I-)KI'IK)K9+A=7C7+C97E=+C?7E/-/1K11-1E/A1-A97C=-AE7C1/3E7A173E9;37;C9?37?79A?7?C9A17A59CA7AE9C797?;?9;9=G='A%)G+%A''G))A'+G++C)/E+)C++I11C/3G5+C11I33C15G75C37G97C59G;9C7;G9=C;?GA;C==G??C=AGA+E+/K//E-1K/)G)+I+1G13K33G35I55G57I77G79I9-I//K1-K-/M/");
        m.put("trauma", "'#+O%KI%/KE11%5C3A+%71'?)%9+'=/'711=+'9/E;9+A=7CC-9G9=7-A97C3/3555?/3A55C/5I19G/9I1AC/=G1AC15G79G17I5?/191C;C1=G5?533?5A33555A?35C5?13937=?3?A7A53A77C=3A?7CC53G75A55C7=357A7?35??7AE71I?3E73G991793C;+7;/C=G9/IC1-973C9)99+C?'9;)C?E;/GA1I;1KE3G?1IA3KA/MC1+C;-E=");
        m.put("largage", "'#'O%O3%'C'+/%)3'-C%)G'--%+/'13%+5'-A%+C'-G%+M'3)%--'7/%-1'/E%-G'/'%/)-1I%3M'5K%5O'7+%C-'E7%C?)KE%CO'GG%GO'IK'+MA-)'---3G'CO)GI'GO)I7)E?+I=+E?-GG;+K?/C;-G?1K;-MA/A;/C=1G;/I?1K=)M?+I?+KA/");
        BOXES = m;
    }
}
