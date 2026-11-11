#include <stdint.h>
#include <stdio.h>

int main(int argc, char **argv) {
    (void)argv;
    const int64_t seed = argc;
    const int64_t limit = 1500000;
    int64_t count = 0;
    int64_t last = 0;

    for (int64_t n = 2; n < limit; n = n + 1) {
        const int64_t m = n + seed - 1;
        int64_t prime = 1;

        for (int64_t d = 2; d * d < m + 1; d = d + 1) {
            if (m % d == 0) {
                prime = 0;
                break;
            }
        }

        if (prime == 1) {
            count = count + 1;
            last = m;
        }
    }

    printf("%lld %lld\n", (long long)count, (long long)last);
    return 0;
}
