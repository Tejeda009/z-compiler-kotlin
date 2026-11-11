#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

extern size_t strnlen(const char *s, size_t maxlen);

int main(void) {
    const int64_t iterations = 2000000;
    int64_t checksum = 0;

    for (int64_t i = 0; i < iterations; i++) {
        unsigned char *p = malloc(64);
        if (p == NULL) return 1;

        memset(p, (int)i, 64);
        checksum += (int64_t)strnlen((char *)p, 8);
        free(p);
    }

    printf("%lld\n", (long long)checksum);
    return 0;
}
