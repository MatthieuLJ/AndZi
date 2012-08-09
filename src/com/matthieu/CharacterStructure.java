package com.matthieu;

import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public class CharacterStructure {
    private static final String TAG = "CharacterStructure";

    String root;
    int height;
    int num_strokes;
    CharacterStructure parent;
    String layout;
    ArrayList<CharacterStructure> children;

    static final HashMap<String, Integer> similar_layouts;
        static {
            similar_layouts = new HashMap<String, Integer>();
            similar_layouts.put("a", 1);
            similar_layouts.put("ra", 1);
            similar_layouts.put("r3a", 1);
            similar_layouts.put("r4a", 1);
            similar_layouts.put("rrefr", 1);
            similar_layouts.put("rrefl", 1);
            similar_layouts.put("ba", 1);

            similar_layouts.put("d", 2);
            similar_layouts.put("rd", 2);
            similar_layouts.put("r3d", 2);
            similar_layouts.put("r4d", 2);
            similar_layouts.put("bd", 2);

            similar_layouts.put("mt", 3);
            similar_layouts.put("mb", 3);
            similar_layouts.put("ml", 3);
            similar_layouts.put("mr", 3);
            similar_layouts.put("mtl", 3);
            similar_layouts.put("msp", 3);
            similar_layouts.put("refh", 3);
            similar_layouts.put("mo", 3);

            similar_layouts.put("lock", 4);
            similar_layouts.put("s", 4);
            similar_layouts.put("st", 4);
            similar_layouts.put("sb", 4);
            similar_layouts.put("sr", 4);
            similar_layouts.put("sl", 4);
            similar_layouts.put("stl", 4);
            similar_layouts.put("sbl", 4);
            similar_layouts.put("str", 4);
            similar_layouts.put("sbr", 4);
            similar_layouts.put("w", 4);
            similar_layouts.put("wt", 4);
            similar_layouts.put("wb", 4);
            similar_layouts.put("wr", 4);
            similar_layouts.put("wl", 4);
            similar_layouts.put("wtr", 4);
            similar_layouts.put("wbr", 4);
            similar_layouts.put("wtl", 4);
            similar_layouts.put("wbl", 4);

            similar_layouts.put("r3tr", 5);
            similar_layouts.put("r4sq", 5);
        }

    public CharacterStructure() {
    }

    public CharacterStructure(String desc) {
        this(build_tree(desc));
    }

    public CharacterStructure(CharacterStructure orig) {
        root = orig.root;
        height = orig.height;
        num_strokes = orig.num_strokes;
        layout = orig.layout;

        parent=null;
        children = new ArrayList<CharacterStructure>();
        for (CharacterStructure c : this.children) {
            CharacterStructure new_child = new CharacterStructure(c);
            new_child.parent = this;
            children.add(new_child);
        }
    }

    private static CharacterStructure build_tree(String desc) {
        int tmp=0;
        CharacterStructure res = new CharacterStructure();
        res.parent = null;
        res.children = new ArrayList<CharacterStructure>();

        tmp = desc.indexOf('=');
        if (tmp == -1) {
            res.layout="c";
            res.height=0;
            res.num_strokes = 1;
        } else {
            res.root = desc.substring(0, tmp);

            tmp += 1; // '='

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
                    CharacterStructure one_child = build_tree(next_part.toString());
                    one_child.parent = res;
                    res.children.add(one_child);
                    next_part = new StringBuffer();
                } else if (((degree != 1) || (!entry.equals("("))) && ((degree != 0) || (!entry.equals(")")))) {
                    next_part.append(entry);
                }

                i+=Character.charCount(desc.codePointAt(i));
            }
            if (next_part.length() != 0) {
                CharacterStructure one_child = build_tree(next_part.toString());
                one_child.parent = res;
                res.children.add(one_child);
            }

            res.num_strokes = 0;
            for (CharacterStructure child : res.children) {
                res.height = Math.max(res.height, child.height+1);
                res.num_strokes += child.num_strokes;
            }
        }
        return res;
    }

    private float weight() {
        float res=0;
        if (layout.equals("a") || layout.equals("d") || layout.equals("lock")) {
            for (CharacterStructure c : children)
                res += c.weight();
        } else if (layout.equals("ra") || layout.equals("rd") || layout.equals("rrefr") || layout.equals("rrefl") || layout.equals("mo")) {
            res = 2*children.get(0).weight();
        } else if (layout.startsWith("r3")) {
            res = 3*children.get(0).weight();
        } else if (layout.startsWith("r4")) {
            res = 4*children.get(0).weight();
        } else if (layout.equals("me")) {
            res = children.get(0).weight();
        } else if (layout.equals("msp") || layout.equals("refh")) {
            res = (float) (1.2*children.get(0).weight());
        } else if (layout.equals("mo")) {
            res = 2*children.get(0).weight();
        } else if (layout.startsWith("m")) {
            res = (float) (0.8*children.get(0).weight());
        } else if (layout.startsWith("s") || layout.startsWith("w") || layout.equals("ba") || layout.equals("bd")) {
            res = (float) (1.5*children.get(0).weight() + 0.5*children.get(1).weight());
        } else if (layout.equals("c")) {
            res=1;
        }

        return res;
    }

    private ArrayList<CharacterStructure> find_in_tree(String to_search, CharacterStructure tree) {
        ArrayList<CharacterStructure> res = null;

        LinkedList<CharacterStructure> next_level_search = new LinkedList<CharacterStructure>();
        LinkedList<CharacterStructure> current_level_search = new LinkedList<CharacterStructure>();
        current_level_search.add(tree);
        while ((res == null) && (current_level_search.size() > 0)) {
            for (CharacterStructure c : current_level_search) {
                if (c.root.equals(to_search)) {
                    if (res == null)
                        res= new ArrayList<CharacterStructure>();
                    res.add(c);
                } else {
                    for (CharacterStructure child : c.children) {
                        if (child.height != 0) {
                            next_level_search.add(child);
                        }
                    }
                }
            }
            current_level_search.clear();
            current_level_search.addAll(next_level_search);
            next_level_search.clear();
        }
        return res;
    }

    private int get_depth(CharacterStructure branch) {
        int depth=0;
        while (branch.parent!=null) {
            depth++;
            branch = branch.parent;
        }
        return depth;
    }

    public float distanceTo(CharacterStructure other) {
        if (other.root.equals(this.root))
            return 0;

        CharacterStructure smaller, taller;

        if (this.height > other.height) {
            smaller = other;
        } else if (this.height < other.height) {
            smaller = this;
        } else if (this.children.size() > other.children.size()) {
            smaller = other;
        } else if (this.children.size() < other.children.size()) {
            smaller = this;
        } else if (this.num_strokes > other.num_strokes) {
            smaller = other;
        } else if (this.num_strokes < other.num_strokes) {
            smaller = this;
        } else {
            smaller = (this.root.compareTo(other.root)<0) ? this:other;
        }
        taller = (smaller == this ? other : this);

        LinkedList<CharacterStructure> next_level_search = new LinkedList<CharacterStructure>();
        LinkedList<CharacterStructure> current_level_search = new LinkedList<CharacterStructure>();
        current_level_search.add(smaller);

        ArrayList<CharacterStructure> res = new ArrayList<CharacterStructure>();
        while ((res.size() == 0) && (current_level_search.size() > 0)) {
            for (CharacterStructure c : current_level_search) {
                ArrayList<CharacterStructure> search = find_in_tree(c.root, taller);
                if (search != null) {
                    res.addAll(search);
                } else {
                    for (CharacterStructure child : c.children) {
                        if (child.height != 0) {
                            next_level_search.add(child);
                        }
                    }
                }
            }
            current_level_search.clear();
            current_level_search.addAll(next_level_search);
            next_level_search.clear();
        }

        if (res.size() == 0) {
            return -1;
        }

        // pick the part with the maximum height or the higher in the tree
        CharacterStructure highest = res.get(0);
        int max_height = -1, depth=-1;
        for (CharacterStructure c : res) {
            if ((max_height == -1) || (max_height < c.height)) {
                highest = c;
                depth = taller.get_depth(c);
                max_height = c.height;
            } else if (max_height == c.height) {
                int new_depth = taller.get_depth(c);
                if (new_depth < depth) {
                    highest = c;
                    depth = new_depth;
                    max_height = c.height;
                }
            }
        }
        while ((highest.parent!=null) && (highest.parent.layout.equals("me")))
            highest = highest.parent;

        res = find_in_tree(highest.root, smaller);
        CharacterStructure matching = res.get(0);
        while ((matching.parent!=null) && (matching.parent.layout.equals("me")))
            matching = matching.parent;

        float difference = (float) (Math.pow(taller.weight() / matching.weight(), 2) * (1+ smaller.height - matching.height));
        float factor=1;

        if (matching.parent != null) {
            // only a part of the smaller character can be found in the bigger one
            String matching_layout = matching.parent.layout;
            String highest_layout = highest.parent.layout;
            if (matching_layout.equals(highest_layout)) {
                if (((matching_layout.equals("a")) || (matching_layout.equals("b")) || (matching_layout.startsWith("s")) || (matching_layout.startsWith("w")))
                        && ((matching.parent.children.indexOf(matching) != highest.parent.children.indexOf(highest)) || (matching.parent.children.size() != highest.parent.children.size()))) {
                    factor = (float) 1.5;
                }
                // else do not change it (same layout, same order if it matters)
            } else if (similar_layouts.get(matching_layout).equals(similar_layouts.get(highest_layout))) {
                factor = 2;
            } else {
                factor= 4;
            }
        }
        difference *= factor;

        Log.d(TAG, "Distance between "+taller.root+" and "+smaller.root+" is "+difference+", factor is "+factor+", highest matching found is "+highest.root);

        return difference;
    }

    public static void do_things_with_characters() {
        String chars[] = new String[] {
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

        CharacterStructure trees[] = new CharacterStructure[chars.length];
        for (int i=0; i<chars.length; i++) {
            trees[i] = build_tree(chars[i]);
        }
        for (int i=0; i<chars.length; i++) {
            for (int j=i+1; j<chars.length; j++) {
                trees[i].distanceTo(trees[j]);
            }
        }

    }
}
