/**
 * Пример экрана для HarmonyOS NEXT (ArkTS/ArkUI).
 *
 * Это компактный стартовый экран: список маршрутов → доска остановок с ЭТА.
 * Скопируйте в entry/src/main/ets/pages/Index.ets проекта DevEco Studio и
 * подключите BusModel.ets из ../model/.
 *
 * Полный Android-интерфейс (fisheye, кольцевая геометрия, жесты короны)
 * переносится через Canvas/анимации ArkUI — см. README.md.
 */

import { http } from '@kit.NetworkKit';
import { ROUTES, DEMO_STOPS, RouteItem, Vehicle, BoardRow, Feed, Board, Geo } from '../model/BusModel';

@Entry
@Component
struct Index {
  @State routes: RouteItem[] = ROUTES;
  @State route: RouteItem | null = null;
  @State rows: BoardRow[] = [];
  @State status: string = '';
  private timer: number = -1;

  aboutToDisappear(): void {
    if (this.timer >= 0) {
      clearInterval(this.timer);
      this.timer = -1;
    }
  }

  async openRoute(r: RouteItem): Promise<void> {
    this.route = r;
    this.status = 'Загрузка…';
    await this.refresh();
    if (this.timer < 0) {
      this.timer = setInterval(() => {
        this.refresh();
      }, 10000);
    }
  }

  async refresh(): Promise<void> {
    const route = this.route;
    if (route === null) {
      return;
    }
    const req = http.createHttp();
    try {
      const resp = await req.request(
        `https://busti.me/ajax/bus/?bus_id=${route.id}`,
        { method: http.RequestMethod.GET, connectTimeout: 5000, readTimeout: 8000 }
      );
      const text: string = resp.responseCode === 200 ? (resp.result as string) : '';
      const vehicles: Vehicle[] = Feed.parse(text, route);
      const now: number = Math.floor(Date.now() / 1000);
      this.rows = Board.build(DEMO_STOPS, vehicles, 0, Geo.CITY_LAT, Geo.CITY_LON, now);
      this.status = `на линии: ${vehicles.length}`;
    } catch (e) {
      this.status = 'Нет связи с сервером';
    } finally {
      req.destroy();
    }
  }

  @Builder
  routeRow(r: RouteItem) {
    Row() {
      Text(r.name)
        .fontSize(18)
        .fontColor('#FFB020')
        .width(40)
        .textAlign(TextAlign.Center)
      Column() {
        Text(r.title)
          .fontSize(11)
          .fontColor('#EAF3FA')
          .maxLines(2)
        Text(this.typeLabel(r.type))
          .fontSize(8)
          .fontColor('#5D7387')
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
    }
    .padding(8)
  }

  typeLabel(type: number): string {
    if (type === 1) return 'Троллейбус';
    if (type === 2) return 'Трамвай';
    if (type === 3) return 'Маршрутка';
    return 'Автобус';
  }

  build() {
    Column({ space: 4 }) {
      Text(this.route === null ? 'МАРШРУТ' : `МАРШРУТ №${this.route.name}`)
        .fontSize(12)
        .fontColor('#FFB020')
        .margin({ top: 8 })

      if (this.route === null) {
        List() {
          ForEach(this.routes, (r: RouteItem) => {
            ListItem() {
              this.routeRow(r)
            }
            .onClick(() => {
              this.openRoute(r);
            })
          }, (r: RouteItem) => `${r.type}-${r.id}`)
        }
        .layoutWeight(1)
        .width('100%')
      } else {
        Text(this.status)
          .fontSize(9)
          .fontColor('#8CA3B5')

        List() {
          ForEach(this.rows, (row: BoardRow) => {
            ListItem() {
              Row() {
                Text(`${row.index + 1}`)
                  .fontSize(9)
                  .fontColor('#5D7387')
                  .width(24)
                Text(row.stop.name)
                  .fontSize(12)
                  .fontColor(row.isUserStop ? '#3DDC97' : '#EAF3FA')
                  .layoutWeight(1)
                  .maxLines(1)
                Text(row.here.length > 0 ? 'здесь' : (row.etaMinutes > 0 ? `~${row.etaMinutes}′` : '—'))
                  .fontSize(10)
                  .fontColor(row.here.length > 0 ? '#3DDC97' : '#FFB020')
                  .width(48)
                  .textAlign(TextAlign.End)
              }
              .padding({ left: 8, right: 8, top: 5, bottom: 5 })
            }
          }, (row: BoardRow) => `${row.index}`)
        }
        .layoutWeight(1)
        .width('100%')

        Button('‹ назад')
          .fontSize(12)
          .height(34)
          .width(120)
          .backgroundColor('#1B3143')
          .onClick(() => {
            this.route = null;
            this.rows = [];
            if (this.timer >= 0) {
              clearInterval(this.timer);
              this.timer = -1;
            }
          })
          .margin({ bottom: 8 })
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#0B1218')
  }
}
