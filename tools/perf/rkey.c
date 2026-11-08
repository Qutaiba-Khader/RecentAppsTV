/* rkey: a virtual remote for on-device tests (root). Presses keys through /dev/uinput, so
   Android sees a real held key and makes its own repeats (~50 ms), as a TV remote does —
   `input keyevent --longpress` sends a single repeat and hid the 1.3 hold bug.
   usage: rkey KEY:HOLD_MS[:GAP_MS] ...   KEY = ok up down left right back (or a Linux code) */
#include <fcntl.h>
#include <linux/uinput.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static int fd;
static void emit(int type, int code, int val) {
    struct input_event e; memset(&e, 0, sizeof e);
    e.type = type; e.code = code; e.value = val;
    write(fd, &e, sizeof e);
}
static int code(const char *k) {
    if (!strcmp(k, "ok")) return KEY_ENTER;
    if (!strcmp(k, "up")) return KEY_UP;
    if (!strcmp(k, "down")) return KEY_DOWN;
    if (!strcmp(k, "left")) return KEY_LEFT;
    if (!strcmp(k, "right")) return KEY_RIGHT;
    if (!strcmp(k, "back")) return KEY_BACK;
    return atoi(k);
}
int main(int argc, char **argv) {
    fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { perror("uinput"); return 1; }
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    int keys[] = {KEY_ENTER, KEY_UP, KEY_DOWN, KEY_LEFT, KEY_RIGHT, KEY_BACK};
    for (unsigned i = 0; i < sizeof keys / sizeof *keys; i++) ioctl(fd, UI_SET_KEYBIT, keys[i]);
    struct uinput_user_dev d; memset(&d, 0, sizeof d);
    strcpy(d.name, "rkey-remote"); d.id.bustype = BUS_VIRTUAL; d.id.vendor = 0x1; d.id.product = 0x1;
    write(fd, &d, sizeof d);
    if (ioctl(fd, UI_DEV_CREATE) < 0) { perror("create"); return 1; }
    usleep(1200 * 1000);                       /* let InputReader pick the device up */
    for (int i = 1; i < argc; i++) {
        char k[32]; int hold = 80, gap = 400;
        if (sscanf(argv[i], "%31[^:]:%d:%d", k, &hold, &gap) < 1) continue;
        int c = code(k);
        emit(EV_KEY, c, 1); emit(EV_SYN, SYN_REPORT, 0);
        usleep(hold * 1000);
        emit(EV_KEY, c, 0); emit(EV_SYN, SYN_REPORT, 0);
        usleep(gap * 1000);
    }
    usleep(300 * 1000);
    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return 0;
}
