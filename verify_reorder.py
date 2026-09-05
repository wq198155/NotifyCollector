"""
复刻 HomeScreen 排序模式下的拖拽换序算法（onDrag 逻辑），
验证「向下拖 -> 往后移、向上拖 -> 往前移、跨多行/越界正确」。

真实算法（HomeScreen.kt onDrag）：
  dragOffset += deltaY                  # 跨多次 onDrag 持续累加
  idx    = list.index(draggedId)
  h      = 该行像素高度（LazyList layoutInfo）
  if h>0:
      steps  = int(dragOffset / h)
      target = clamp(idx + steps, 0, len-1)
      if target != idx: 把 dragged 移到 target; dragOffset = 0   # 仅交换后归零
"""

def drag_full(order, dragged_id, total_px, heights, step=10):
    o = list(order)
    drag_offset = 0
    remaining = total_px
    while remaining != 0:
        d = step if remaining > 0 else -step
        if abs(d) > abs(remaining):
            d = remaining
        remaining -= d
        drag_offset += d
        idx = o.index(dragged_id)
        h = heights.get(dragged_id, 0)
        if h > 0:
            steps = int(drag_offset / h)
            target = max(0, min(len(o) - 1, idx + steps))
            if target != idx:
                o.pop(idx)
                o.insert(target, dragged_id)
                drag_offset = 0
    return o

# 4 个分组，等行高 100px
order0 = ["A", "B", "C", "D"]
heights = {"A": 100, "B": 100, "C": 100, "D": 100}

cases = []
cases.append(("A 向下拖 250px(跨2行)", drag_full(order0, "A", 250, heights), ["B", "C", "A", "D"]))
cases.append(("A 向下拖 300px(到底)",  drag_full(order0, "A", 300, heights), ["B", "C", "D", "A"]))
cases.append(("A 向下拖 350px(越界夹紧)", drag_full(order0, "A", 350, heights), ["B", "C", "D", "A"]))
cases.append(("D 向上拖 200px(跨2行)",  drag_full(order0, "D", -200, heights), ["A", "D", "B", "C"]))
cases.append(("B 向上拖 50px(不足一行不动)", drag_full(order0, "B", -50, heights), ["A", "B", "C", "D"]))
cases.append(("B 向下拖 100px(恰一行)", drag_full(order0, "B", 100, heights), ["A", "C", "B", "D"]))
heights2 = {"A": 120, "B": 80, "C": 100, "D": 100}
cases.append(("不等行高 A 向下拖 120px", drag_full(order0, "A", 120, heights2), ["B", "A", "C", "D"]))

allpass = True
for name, got, exp in cases:
    ok = got == exp
    allpass = allpass and ok
    print(f"[{'PASS' if ok else 'FAIL'}] {name}: 得到 {got} 期望 {exp}")

# 保存后顺序应被写为 0,1,2,3（对应最终列表）
print("\n所有用例:", "PASS ✅" if allpass else "FAIL ❌")
