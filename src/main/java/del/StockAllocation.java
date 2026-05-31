package del;

/**
 * @author ab
 * @date 2026/5/26
 **/
import java.util.*;

public class StockAllocation {

    /**
     * 库存分配
     * @param stock 各仓库当前库存数组
     * @param orderQty 订单需求数量
     * @return int[] 每个仓库分配数量（与stock等长），null表示总库存不足
     */
    public static int[] allocate(int[] stock, int orderQty) {
        // 边界校验
        if (stock == null || stock.length == 0) return new int[0];
        if (orderQty <= 0) return new int[stock.length]; // 不需要分配

        long totalStock = 0;
        for (int s : stock) {
            totalStock += s;
        }
        if (totalStock < orderQty) {
            System.out.println("库存不足，最多可分配" + totalStock + "件");
            return null; // 总库存不够
        }

        int n = stock.length;
        int[] result = new int[n];

        // 用最大堆（按库存量降序），存储 [仓库索引, 当前剩余库存]
        PriorityQueue<int[]> maxHeap = new PriorityQueue<>(
                (a, b) -> Integer.compare(b[1], a[1]) // 降序
        );
        for (int i = 0; i < n; i++) {
            if (stock[i] > 0) {
                maxHeap.offer(new int[]{i, stock[i]});
            }
        }

        int remaining = orderQty;

        while (remaining > 0 && !maxHeap.isEmpty()) {
            int[] top = maxHeap.poll();
            int warehouseIdx = top[0];
            int currentStock = top[1];

            // 每次取该仓库库存的50%（向下取整），但不超过剩余需求
            int take = Math.min(currentStock / 2, remaining);

            // 特殊处理：如果这是最后一个有货的仓库，直接取完
            if (maxHeap.isEmpty()) {
                take = Math.min(currentStock, remaining);
            }

            result[warehouseIdx] += take;
            currentStock -= take;
            remaining -= take;

            // 如果还有剩余库存，重新入堆
            if (currentStock > 0) {
                maxHeap.offer(new int[]{warehouseIdx, currentStock});
            }
        }

        System.out.println("分配结果：" + Arrays.toString(result));
        return result;
    }

    public static void main(String[] args) {
        int[] stock = {100, 50, 200, 80};
        allocate(stock, 120);
    }
}
