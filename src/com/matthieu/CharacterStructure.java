package com.matthieu;

import android.util.Log;

import java.util.ArrayList;

public class CharacterStructure {
    private static final String TAG = "CharacterStructure";
    
    static class character_tree {
        String root;
        int height;
        character_tree parent;
        String layout;
        ArrayList<character_tree> children;
    }

    private character_tree build_tree(String desc) {
        int tmp=0;
        character_tree res = new character_tree();
        res.parent = null;

        tmp = Character.charCount(desc.codePointAt(0));
        res.root = desc.substring(0, tmp);
        res.children = new ArrayList<character_tree>();

        tmp += 1; // =

        int degree=0;
        StringBuffer next_part = new StringBuffer();
        for (int i=tmp; i<desc.length(); ) {
            final String entry = desc.substring(i, i + Character.charCount(desc.codePointAt(i)));
            if (entry.equals("(") ) {
                if (degree == 0) {
                    res.layout = next_part.toString();
                    next_part = new StringBuffer();
                }
                degree++;
            } else if (entry.equals(")"))
                degree--;

            if ((degree == 1) && (entry.equals(","))) {
                character_tree one_child = build_tree(next_part.toString());
                one_child.parent = res;
                res.children.add(one_child);
                next_part = new StringBuffer();
            } else if (((degree != 1) || (!entry.equals("("))) && ((degree != 0) || (!entry.equals(")")))) {
                next_part.append(entry);
            }

            i+=Character.charCount(desc.codePointAt(i));
        }
        if (next_part.length() != 0) {
            character_tree one_child = build_tree(next_part.toString());
            one_child.parent = res;
            res.children.add(one_child);
        }
        res.height=0;
        for (character_tree child : res.children) {
            res.height = Math.max(res.height, child.height+1);
        }
        return res;
    }

    private void do_things_with_characters() {
        String trees[] = new String[] {
                "土=d(十=lock(㇐,㇑),㇐)",
                "士=msp(土=d(十=lock(㇐,㇑),㇐))",
                "午=wtl(干=d(㇐,十=lock(㇐,㇑)),㇔)",
                "牛=wtl(37024=lock(二=rd(㇐),㇑),㇔)",
                "年=d(𠂉=a(㇓,㇐),㐄=lock(十=lock(㇐,㇑),㇜))",
                "本=wb(木=w(十=lock(㇐,㇑),八=a(㇓,㇏)),㇐)",
                "木=w(十=lock(㇐,㇑),八=a(㇓,㇏))",
                "体=a(亻=d(㇒,㇑),本=wb(木=w(十=lock(㇐,㇑),八=a(㇓,㇏)),㇐))",
                "休=a(亻=d(㇒,㇑),木=w(十=lock(㇐,㇑),八=a(㇓,㇏)))",
                "已=msp(己=d(37035=d(㇕,㇐),㇟))",
                "己=d(37035=d(㇕,㇐),㇟)",
                "人=a(㇓,㇏)",
                "入=refh(人=a(㇓,㇏))",
                "找=a(扌=w(㇚,二=rd(㇐)),戈=wtr(37427=lock(37141=w(㇂,㇐),㇓),㇔))",
                "我=a(手=d(㇒,扌=w(㇚,二=rd(㇐))),戈=wtr(37427=lock(37141=w(㇂,㇐),㇓),㇔))",
                "钱=a(钅=d(𠂉=a(㇓,㇐),38008=lock(㇟,二=rd(㇐))),戋=wtr(37837=w(37193=lock(㇂,㇒),二=rd(㇐)),㇔))",
                "段=a(60782=a(㇑,99876=r4d(㇐)),殳=d(几=a(㇓,㇠),又=lock(㇇,㇏)))",
                "锻=a(钅=d(𠂉=a(㇓,㇐),38008=lock(㇟,二=rd(㇐))),段=a(60782=a(㇑,99876=r4d(㇐)),殳=d(几=a(㇓,㇠),又=lock(㇇,㇏))))",
                "煅=a(火=w(人=a(㇓,㇏),99951=ra(㇔)),段=a(60782=a(㇑,99876=r4d(㇐)),殳=d(几=a(㇓,㇠),又=lock(㇇,㇏))))",
                "包=lock(勹=wtl(㇆,㇒),巳=w(㇟,37035=d(㇕,㇐)))",
                "抱=a(扌=w(㇚,二=rd(㇐)),包=lock(勹=wtl(㇆,㇒),巳=w(㇟,37035=d(㇕,㇐))))",
                "饱=a(饣=d(⺈=mt(勹=wtl(㇆,㇒)),㇟),包=lock(勹=wtl(㇆,㇒),巳=w(㇟,37035=d(㇕,㇐))))",
                "分=d(八=a(㇓,㇏),刀=str(㇆,㇓))",
                "份=a(亻=d(㇒,㇑),37509=d(人=a(㇓,㇏),刀=str(㇆,㇓)))",
                "纷=a(纟=d(37217=rd(㇛),㇐),分=d(八=a(㇓,㇏),刀=str(㇆,㇓)))",
                "工=d(㇐,丄=d(㇑,㇐))",
                "红=a(纟=d(37217=rd(㇛),㇐),工=d(㇐,丄=d(㇑,㇐)))",
                "江=a(氵=d(⺀=rd(㇔),㇀),工=d(㇐,丄=d(㇑,㇐)))",
                "会=d(人=a(㇓,㇏),云=d(二=rd(㇐),厶=wbr(㇛,㇔)))",
                "令=d(亽=d(人=a(㇓,㇏),㇔),龴=wbr(㇇,㇔))",
                "今=d(亽=d(人=a(㇓,㇏),㇔),㇇)",
                "括=a(扌=w(㇚,二=rd(㇐)),舌=d(千=d(㇒,十=lock(㇐,㇑)),口=msp(囗=wb(冂=lock(㇑,㇆),㇐))))",
                "活=a(氵=d(⺀=rd(㇔),㇀),舌=d(千=d(㇒,十=lock(㇐,㇑)),口=msp(囗=wb(冂=lock(㇑,㇆),㇐))))",
                "话=a(讠=wt(㇊,㇔),舌=d(千=d(㇒,十=lock(㇐,㇑)),口=msp(囗=wb(冂=lock(㇑,㇆),㇐))))",
                "斤=stl(⺁=d(㇒,㇓),丅=d(㇐,㇑))",
                "厅=stl(厂=a(㇓,㇐),丁=msp(丅=d(㇐,㇑)))",
                "里=d(田=st(冂=lock(㇑,㇆),土=d(十=lock(㇐,㇑),㇐)),土=d(十=lock(㇐,㇑),㇐))",
                "星=d(日=w(口=msp(囗=wb(冂=lock(㇑,㇆),㇐)),㇐),生=wtl(龶=msp(丰=lock(三=d(㇐,二=rd(㇐)),㇑)),㇔))",
                "美=d(羊=d(37145=d(㇐,37024=lock(二=rd(㇐),㇑)),丷=msp(八=a(㇓,㇏))),大=w(人=a(㇓,㇏),㇐))",
                "关=d(䒑=d(丷=msp(八=a(㇓,㇏)),㇐),大=w(人=a(㇓,㇏),㇐))",
                "天=d(㇐,大=w(人=a(㇓,㇏),㇐))",
                "夫=w(大=w(人=a(㇓,㇏),㇐),㇐)",
                "失=wtl(夫=w(大=w(人=a(㇓,㇏),㇐),㇐),㇔)",
                "亲=d(立=d(亠=wt(㇐,㇔),䒑=d(丷=msp(八=a(㇓,㇏)),㇐)),朩=d(木=w(十=lock(㇐,㇑),八=a(㇓,㇏)),㇚))",
                "章=d(立=d(亠=wt(㇐,㇔),䒑=d(丷=msp(八=a(㇓,㇏)),㇐)),早=d(日=w(口=msp(囗=wb(冂=lock(㇑,㇆),㇐)),㇐),十=lock(㇐,㇑)))",
                "幸=d(土=d(十=lock(㇐,㇑),㇐),𢆉=d(丷=msp(八=a(㇓,㇏)),干=d(㇐,十=lock(㇐,㇑))))",
                "莘=d(卄=lock(10001=ra(㇑),㇐),辛=d(立=d(亠=wt(㇐,㇔),䒑=d(丷=msp(八=a(㇓,㇏)),㇐)),十=lock(㇐,㇑)))",
                "辛=d(立=d(亠=wt(㇐,㇔),䒑=d(丷=msp(八=a(㇓,㇏)),㇐)),十=lock(㇐,㇑))",
                "良=wt(艮=stl(𢀳=w(㇟,彐=str(㇕,二=rd(㇐))),37074=d(㇓,㇏)),㇔)",
                "食=d(亽=d(人=a(㇓,㇏),㇔),艮=stl(𢀳=w(㇟,彐=str(㇕,二=rd(㇐))),37074=d(㇓,㇏)))",
                "艮=stl(𢀳=w(㇟,彐=str(㇕,二=rd(㇐))),37074=d(㇓,㇏))",
                "身=lock(𨈑=w(10019=wt(𠂎=refh(卩=w(㇑,㇆)),㇔),二=rd(㇐)),㇓)",
                "世=lock(廿=w(卄=lock(10001=ra(㇑),㇐),㇐),㇗)",
                "也=lock(㇟,37387=w(㇆,㇑))",
                "衣=d(亠=wt(㇐,㇔),𧘇=a(37143=d(㇒,㇗),37074=d(㇓,㇏)))",
                "死=stl(歹=d(㇐,夕=mtl(勺=w(勹=wtl(㇆,㇒),㇔))),匕=sbl(㇟,㇒))",
                "夜=d(亠=wt(㇐,㇔),38480=a(亻=d(㇒,㇑),41900=w(夂=wtl(又=lock(㇇,㇏),㇒),㇔)))",
                "内=lock(冂=lock(㇑,㇆),人=a(㇓,㇏))",
                "肉=lock(冂=lock(㇑,㇆),仌=rd(人=a(㇓,㇏)))",
                "两=d(㇐,37671=lock(从=ra(人=a(㇓,㇏)),冂=lock(㇑,㇆)))",
                "干=d(㇐,十=lock(㇐,㇑))",
                "千=d(㇒,十=lock(㇐,㇑))",
                "于=msp(干=d(㇐,十=lock(㇐,㇑)))",
                "戌=st(戊=a(厂=a(㇓,㇐),戈=wtr(37427=lock(37141=w(㇂,㇐),㇓),㇔)),㇐)",
                "戍=st(戊=a(厂=a(㇓,㇐),戈=wtr(37427=lock(37141=w(㇂,㇐),㇓),㇔)),㇔)",
                "成=a(万=d(㇐,37184=w(㇓,㇆)),戈=wtr(37427=lock(37141=w(㇂,㇐),㇓),㇔))",
                "田=st(冂=lock(㇑,㇆),土=d(十=lock(㇐,㇑),㇐))",
                "由=d(土=d(十=lock(㇐,㇑),㇐),冂=lock(㇑,㇆))",
                "甲=lock(冂=lock(㇑,㇆),37024=lock(二=rd(㇐),㇑))",
                "申=lock(日=w(口=msp(囗=wb(冂=lock(㇑,㇆),㇐)),㇐),㇑)",
                "母=lock(10013=lock(㇗,㇆),10014=d(㇔,㇐,㇔))",
                "毋=lock(10013=lock(㇗,㇆),十=lock(㇐,㇑))",
                "勿=w(勹=wtl(㇆,㇒),37125=ra(㇓))",
                "匆=lock(勿=w(勹=wtl(㇆,㇒),37125=ra(㇓)),㇔)",
                "未=wt(木=w(十=lock(㇐,㇑),八=a(㇓,㇏)),㇐)",
                "末=w(木=w(十=lock(㇐,㇑),八=a(㇓,㇏)),㇐)",
                "采=d(⺤=mt(爪=st(37409=a(⺁=d(㇒,㇓),㇏),㇑)),木=w(十=lock(㇐,㇑),八=a(㇓,㇏)))",
                "釆=d(㇒,米=w(木=w(十=lock(㇐,㇑),八=a(㇓,㇏)),丷=msp(八=a(㇓,㇏))))",
                "凹=mo(凵=lock(㇗,㇑))",
                "凸=mo(丄=d(㇑,㇐))",
                "猫=a(犭=lock(㇁,37125=ra(㇓)),苗=d(卄=lock(10001=ra(㇑),㇐),田=st(冂=lock(㇑,㇆),土=d(十=lock(㇐,㇑),㇐))))",
                "描=a(扌=w(㇚,二=rd(㇐)),苗=d(卄=lock(10001=ra(㇑),㇐),田=st(冂=lock(㇑,㇆),土=d(十=lock(㇐,㇑),㇐))))",
                "岗=d(山=lock(㇑,凵=lock(㇗,㇑)),冈=st(冂=lock(㇑,㇆),乂=lock(㇓,㇏)))",
                "尚=st(37737=d(⺌=w(㇑,丷=msp(八=a(㇓,㇏))),冂=lock(㇑,㇆)),口=msp(囗=wb(冂=lock(㇑,㇆),㇐)))",
                "低=a(亻=d(㇒,㇑),氐=wb(氏=lock(37143=d(㇒,㇗),37141=w(㇂,㇐)),㇔))",
                "底=stl(广=wt(厂=a(㇓,㇐),㇔),氐=wb(氏=lock(37143=d(㇒,㇗),37141=w(㇂,㇐)),㇔))",
                "抵=a(扌=w(㇚,二=rd(㇐)),氐=wb(氏=lock(37143=d(㇒,㇗),37141=w(㇂,㇐)),㇔))",
                "纸=a(纟=d(37217=rd(㇛),㇐),氏=lock(37143=d(㇒,㇗),37141=w(㇂,㇐)))",
                "昭=a(日=w(口=msp(囗=wb(冂=lock(㇑,㇆),㇐)),㇐),召=d(刀=str(㇆,㇓),口=msp(囗=wb(冂=lock(㇑,㇆),㇐))))",
                "绍=a(纟=d(37217=rd(㇛),㇐),召=d(刀=str(㇆,㇓),口=msp(囗=wb(冂=lock(㇑,㇆),㇐))))",
                "邵=a(召=d(刀=str(㇆,㇓),口=msp(囗=wb(冂=lock(㇑,㇆),㇐))),阝=w(㇑,㇌))",
                "超=sbl(走=d(土=d(十=lock(㇐,㇑),㇐),龰=msp(止=sbl(⺊=a(㇑,㇐),丄=d(㇑,㇐)))),召=d(刀=str(㇆,㇓),口=msp(囗=wb(冂=lock(㇑,㇆),㇐))))",
                "经=a(纟=d(37217=rd(㇛),㇐),37030=d(龴=wbr(㇇,㇔),工=d(㇐,丄=d(㇑,㇐))))",
                "轻=a(车=lock(七=w(㇟,㇐),十=lock(㇐,㇑)),37030=d(龴=wbr(㇇,㇔),工=d(㇐,丄=d(㇑,㇐))))",
                "赏=d(37062=d(龸=d(⺌=w(㇑,丷=msp(八=a(㇓,㇏))),冖=msp(冂=lock(㇑,㇆))),口=msp(囗=wb(冂=lock(㇑,㇆),㇐))),贝=d(冂=lock(㇑,㇆),人=a(㇓,㇏)))",
                "觉=d(37044=d(⺍=msp(37543=a(㇓,㇓,㇏)),冖=msp(冂=lock(㇑,㇆))),见=d(冂=lock(㇑,㇆),儿=a(㇓,㇟)))",
                "见=d(冂=lock(㇑,㇆),儿=a(㇓,㇟))",
                "苋=d(卄=lock(10001=ra(㇑),㇐),见=d(冂=lock(㇑,㇆),儿=a(㇓,㇟)))",
                "山=lock(㇑,凵=lock(㇗,㇑))",
                "仙=a(亻=d(㇒,㇑),山=lock(㇑,凵=lock(㇗,㇑)))",
                "出=rd(山=lock(㇑,凵=lock(㇗,㇑)))",
                "辈=d(非=rrefl(56871=a(㇑,三=d(㇐,二=rd(㇐)))),车=lock(七=w(㇟,㇐),十=lock(㇐,㇑)))",
                "素=d(龶=msp(丰=lock(三=d(㇐,二=rd(㇐)),㇑)),糸=d(幺=wbr(37217=rd(㇛),㇔),小=w(㇚,八=a(㇓,㇏))))"
        };

        character_tree res = build_tree(trees[0]);
        Log.d(TAG, "Got a tree...");
    }
}
