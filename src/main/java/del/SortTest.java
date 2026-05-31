package del;

import java.util.Arrays;

/**
 * @author ab
 * @date 2026/5/26
 **/
public class SortTest {
    public static void main(String[] args) {
        int[] arr = new int[]{9, 2, 3, 6, 7, 5};
        sort3(arr);
    }

    private static void sort1(int[] arr) {
        for (int i = 0; i < arr.length; i++) {
            for (int j = i + 1; j < arr.length; j++) {
                if (arr[i] > arr[j]) {
                    int temp = arr[i];
                    arr[i] = arr[j];
                    arr[j] = temp;
                }
            }
        }
        Arrays.stream(arr).forEach(i -> System.out.println(i));
    }

    private static void sort2(int[] arr) {
        for (int i = 0; i < arr.length; i++) {
            int mindix = i;
            for (int j = i + 1; j < arr.length; j++) {
                if(arr[mindix] > arr[j]){
                    mindix = j;
                }
            }
            if(mindix != i){
                int temp = arr[i];
                arr[i] = arr[mindix];
                arr[mindix] = temp;
            }
        }
        Arrays.stream(arr).forEach(i -> System.out.println(i));
    }

    private static void sort3(int[] arr){
        for (int i = 1; i < arr.length; i++) {
            int key = arr[i];
            int j = i-1;

            while (j >= 0 && arr[j] > key){
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j+1] = key;
        }
        Arrays.stream(arr).forEach(i -> System.out.println(i));
    }

}
