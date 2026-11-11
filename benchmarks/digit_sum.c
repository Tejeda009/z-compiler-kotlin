#include <stdint.h>
#include <stdio.h>

static int64_t digit_sum(int64_t value) {
    int64_t n = value;
    int64_t sum = 0;

    for (int64_t guard = 0; n > 0; guard = guard + 1) {
        sum = sum + n % 10;
        n = n / 10;
    }

    return sum;
}

int main(int argc, char **argv) {
    (void)argv;
    const int64_t seed = argc;
    const int64_t limit = 60000000;
    int64_t total = 0;

    for (int64_t i = 1; i < limit; i = i + 1) {
        total = total + digit_sum(i + seed - 1);
    }

    printf("%lld\n", (long long)total);
    return 0;
}
