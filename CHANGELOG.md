# Change Log
All notable changes to this project will be documented in this file. This 
change log follows the conventions of 
[keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

## [0.6.9] — 2022-03-31
### Added
- `on-complement-of` event matcher utility that matches the complement of 
  the matcher it wraps. For example:
    - `(on-complement-of (on-type :do-not-match))` will match all types that
      are not `:do-not-match`
    - `(on-complement-of (on-types [:do-not-match-1 :do-not-match-2]))` will 
      match all types that are not `:do-not-match-1` or `:do-not-match-2`
    - `(on-complement-of (on :do-not-process))` will match all events that 
      have a falsy property `:do-not-process`

## [0.6.8] — 2020-08-20

## [0.6.7] — 2020-07-06

## [0.6.6] — 2020-07-03

## [0.6.5] — 2020-01-31

## [0.6.4] — 2019-08-23
### Changed
- Renamed `from` to `from-channel` for consistency with `on-type` (breaking).

## [0.6.3] — 2019-08-22
### Added
- `on` event matcher expecting a predicate of the event.
- `on-every` event matcher, matching all events.

## [0.6.2] — 2019-08-22
### Added
- Codox.

## [0.6.1] — 2019-08-22
### Changed
- Renamed `on` to `on-type` (breaking).

## [0.6.0] - 2019-05-22
- Initial release.

[0.6.0]: https://github.com/your-name/vent/compare/0.1.0...0.6.0
[0.6.1]: https://github.com/your-name/vent/compare/0.6.0...0.6.1
[0.6.2]: https://github.com/your-name/vent/compare/0.6.1...0.6.2
[0.6.3]: https://github.com/your-name/vent/compare/0.6.2...0.6.3
[0.6.4]: https://github.com/your-name/vent/compare/0.6.3...0.6.4
[0.6.5]: https://github.com/your-name/vent/compare/0.6.4...0.6.5
[0.6.6]: https://github.com/your-name/vent/compare/0.6.5...0.6.6
[0.6.7]: https://github.com/your-name/vent/compare/0.6.6...0.6.7
[0.6.8]: https://github.com/your-name/vent/compare/0.6.7...0.6.8
[0.6.9]: https://github.com/your-name/vent/compare/0.6.8...0.6.9
[Unreleased]: https://github.com/your-name/vent/compare/0.6.9...HEAD
