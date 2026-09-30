# Tour media

Real tour photos go in `/media/tours/<tour-slug>/<file>.(avif|webp|jpg|png)`,
matching the paths registered for each tour in the ERP (UX-0 media model).
Until a tour has approved media, `TourMedia.vue` renders a branded placeholder
in the tour's category colour — never a stock photo.
